package ru.pts.estdex.mvc.processors.actions;

import com.ptc.core.components.beans.ObjectBean;
import com.ptc.core.components.forms.DefaultObjectFormProcessor;
import com.ptc.core.components.forms.FormProcessingStatus;
import com.ptc.core.components.forms.FormResult;
import com.ptc.core.components.util.FeedbackMessage;
import com.ptc.core.meta.common.TypeIdentifier;
import com.ptc.core.meta.common.TypeIdentifierHelper;
import com.ptc.core.ui.resources.FeedbackType;
import com.ptc.netmarkets.model.NmOid;
import com.ptc.netmarkets.util.beans.NmCommandBean;
import com.ptc.netmarkets.util.misc.NmContext;
import com.ptc.windchill.mpml.resource.MPMTooling;
import org.apache.commons.io.FilenameUtils;
import wt.associativity.EquivalenceLink;
import wt.content.*;
import wt.doc.WTDocument;
import wt.enterprise.RevisionControlled;
import wt.fc.*;
import wt.fc.collections.WTHashSet;
import wt.fc.collections.WTKeyedHashMap;
import wt.fc.collections.WTKeyedMap;
import wt.part.WTPart;
import wt.part.WTPartHelper;
import wt.pom.Transaction;
import wt.series.MultilevelSeries;
import wt.series.SeriesHelper;
import wt.series.SeriesServerHelper;
import wt.session.SessionHelper;
import wt.util.WTException;
import wt.util.WTPropertyVetoException;
import wt.vc.*;
import wt.vc.views.ViewReference;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.*;

/**
 * Created by Komyshenets on 30.03.2017.
 */
public class AlignVersionProcessor extends DefaultObjectFormProcessor {
    Properties validTypes = null;

    @Override
    public FormResult doOperation(NmCommandBean nmCommandBean, List<ObjectBean> list) throws WTException {
        String changeAllPart = nmCommandBean.getTextParameter("up-all-linked-parts");
        String changeAllTooling = nmCommandBean.getTextParameter("up-all-eq-tools");

        WTKeyedHashMap mapTargetObjects = new WTKeyedHashMap();
        WTKeyedHashMap mapTargetObjectsAndFiles = new WTKeyedHashMap();

        for (Object selectedElement : nmCommandBean.getSelected()) {
            NmOid targetNmOid = ((NmContext) selectedElement).getTargetOid();
            String targetOid = targetNmOid.toString();

            Object newVersion = nmCommandBean.getText().get(targetOid);
            WTKeyedHashMap map = compileTargetObjects(targetOid, newVersion, "on".equals(changeAllPart), "on".equals(changeAllTooling));
            mapTargetObjects.putAll(map);


            HashMap newFiles = getNewFiles(nmCommandBean, targetNmOid);
            if (!newFiles.isEmpty()) {
                Persistable object = new ReferenceFactory().getReference(targetOid).getObject();
                mapTargetObjectsAndFiles.put(object, newFiles);
            }
        }

        return doProcessor(mapTargetObjects, mapTargetObjectsAndFiles);
    }

    private FormResult doProcessor(WTKeyedHashMap mapTargetObjects, WTKeyedHashMap mapTargetObjectsAndFiles) throws WTException {
        Transaction transaction = new Transaction();
        try {
            transaction.start();

            for (Object entry : mapTargetObjectsAndFiles.entrySet()) {
                WTKeyedMap.WTEntry wtEntry = (WTKeyedHashMap.WTEntry) entry;
                ObjectReference key = (ObjectReference) wtEntry.getKey();
                Persistable targetObject = key.getObject();
                if (targetObject instanceof ContentHolder) {
                    HashMap value = (HashMap) wtEntry.getValue();

                    String fileName = (String) value.keySet().iterator().next();
                    File file = (File) value.get(fileName);

                    ApplicationData applicationData;
                    ContentItem primaryContent = ContentHelper.service.getPrimaryContent(ObjectReference.newObjectReference(targetObject));
                    if (primaryContent == null) {
                        applicationData = ApplicationData.newApplicationData((ContentHolder) targetObject);
                    } else {
                        primaryContent.setModifiedBy(SessionHelper.manager.getPrincipalReference());
                        applicationData = (ApplicationData) primaryContent;
                    }
                    applicationData.setRole(ContentRoleType.PRIMARY);
                    applicationData.setFileName(fileName);
                    {
                        DataFormatReference dataFormatReference = null;
                        try {
                            String extension = FilenameUtils.getExtension(fileName).toUpperCase();
                            DataFormat formatByName = ContentHelper.service.getFormatByName(extension);
                            dataFormatReference = DataFormatReference.newDataFormatReference(formatByName);
                        } catch (WTException ignored) {
                        }
                        if (dataFormatReference != null) {
                            applicationData.setFormat(dataFormatReference);
                            if (targetObject instanceof _FormatContentHolder) {
                                ((_FormatContentHolder) targetObject).setFormat(dataFormatReference);
                            }
                        }
                    }
                    ContentServerHelper.service.updateContent((ContentHolder) targetObject, applicationData, new FileInputStream(file));
                    {
                        VersionControlHelper.setIterationModifier((Iterated) targetObject, SessionHelper.manager.getPrincipalReference());
                        PersistenceServerHelper.manager.update(targetObject);
                    }
                }
            }

            for (Object entry : mapTargetObjects.entrySet()) {
                WTKeyedMap.WTEntry wtEntry = (WTKeyedHashMap.WTEntry) entry;
                ObjectReference key = (ObjectReference) wtEntry.getKey();
                // TODO: 27.09.2017 change Revision hire
//                try {
                ChangeRevisionForViewHelper.changeRevision((Versioned) key.getObject(), wtEntry.getValue().toString(), false);
//                        VersionControlHelper.service.changeRevision((Versioned) key.getObject(), wtEntry.getValue().toString());
//                } catch (VersionControlException vce) {
//                    System.out.println(vce.getLocalizedMessage());
////                    changeRevision((Versioned) key.getObject(), wtEntry.getValue().toString());
//                }
            }


            FormResult formResult = new FormResult();
            String msg = mapTargetObjects.isEmpty() ? "Нет объектов для изменения" : "Версии изменились";
            FeedbackMessage message = new FeedbackMessage(FeedbackType.SUCCESS, null, null, null, new String[]{msg});
            formResult.addFeedbackMessage(message);
            formResult.setSkipPageRefresh(false);

            transaction.commit();
            return formResult;
        } catch (Exception e) {
            transaction.rollback();
            e.printStackTrace();
            FormResult formResult = new FormResult();
            String msg = e.getLocalizedMessage();
            FeedbackMessage message = new FeedbackMessage(FeedbackType.FAILURE, null, null, null, new String[]{msg});
            formResult.addFeedbackMessage(message);
            formResult.setStatus(FormProcessingStatus.FAILURE);
            formResult.setSkipPageRefresh(true);

            return formResult;
        }
    }

    private void changeRevision(Versioned object, String s) throws WTException, WTPropertyVetoException {
        MultilevelSeries series = VersionControlHelper.getVersionIdentifier(object).getSeries();
        series.setValueWithoutValidating(s);
        SeriesHelper.validateInSeriesRange(series);
        VersionIdentifier versionIdentifier = VersionIdentifier.newVersionIdentifier(series);
        String seriesSortId = SeriesServerHelper.getSeriesSortId(series);
        versionIdentifier.setVersionSortId(seriesSortId);
        WTHashSet set = new WTHashSet();

        QueryResult queryResult = VersionControlHelper.service.allIterationsFrom(object);
        while (queryResult.hasMoreElements()) {
            Versioned versioned = (Versioned) queryResult.nextElement();
            if (VersionControlHelper.inSameBranch(object, versioned)) {
                VersionControlServerHelper.setVersionIdentifier(versioned, versionIdentifier, false);
                set.add(versioned);
            }
        }
        PersistenceServerHelper.manager.update(set, true);

    }

    private HashMap getNewFiles(NmCommandBean nmCommandBean, NmOid targetOid) throws WTException {
        HashMap result = new HashMap();

        String targetOidOR = targetOid.getOidObject().getStringValue();
        Object map = nmCommandBean.getMap().get("fileUploadMap");
        if (map != null) {
            HashMap fileUploadMap = (HashMap) map;
            Object file = fileUploadMap.get("fileData$" + targetOidOR);
            String name = ((String[]) nmCommandBean.getParameterMap().get("fileData$" + targetOidOR))[0];
            if (name.contains("\\"))
                name = name.substring(name.lastIndexOf("\\") + 1, name.length());
            result.put(name, file);
        }
        return result;
    }


    private WTKeyedHashMap compileTargetObjects(String oid, Object newVersion, boolean addLinkedParts, boolean addLinkedTooling) throws WTException {
        WTKeyedHashMap mapTargetObject = new WTKeyedHashMap();

        if (newVersion == null || "".equals(newVersion)) return mapTargetObject;
        Persistable persistable = new ReferenceFactory().getReference(oid).getObject();
        mapTargetObject.putAll(checkVersion(null, persistable, newVersion));

        if (addLinkedParts && persistable instanceof WTDocument && checkValidType(persistable)) {
            mapTargetObject.putAll(getAllLinkedParts((WTDocument) persistable, newVersion));
        }
        if (addLinkedTooling) {
            mapTargetObject.putAll(getAllLinkedTools((WTDocument) persistable, newVersion));
//
//            WTKeyedHashMap eqTooling = new WTKeyedHashMap();
//
//            for (Object o : mapTargetObject.keySet()) {
//                Persistable object = ((ObjectReference) o).getObject();
//                if (object instanceof WTPart) {
//                    eqTooling.putAll(getAllEquivalenceTooling((WTPart) object, newVersion));
//                }
//            }
//            mapTargetObject.putAll(eqTooling);
        }
        return mapTargetObject;
    }

    private Map getAllLinkedTools(WTDocument persistable, Object newVersion) throws WTException {
        WTKeyedHashMap result = new WTKeyedHashMap();
        QueryResult describesWTParts = WTPartHelper.service.getDescribesWTParts(persistable);
        while (describesWTParts.hasMoreElements()) {
            Object o = describesWTParts.nextElement();
            if (o.getClass().getName().contains(MPMTooling.class.getName())) {
                MPMTooling part = (MPMTooling) o;
                part = (MPMTooling) getLastRevision(part);
                if (part != null)
                    result.putAll(checkVersion(persistable, part, newVersion));
            }
        }
        return result;
    }

    private Map getAllLinkedParts(WTDocument document, Object newVersion) throws WTException {
        WTKeyedHashMap result = new WTKeyedHashMap();
        QueryResult describesWTParts = WTPartHelper.service.getDescribesWTParts(document);
        while (describesWTParts.hasMoreElements()) {
            Object o = describesWTParts.nextElement();
            if (o.getClass().getName().contains(WTPart.class.getName())) {
                WTPart part = (WTPart) o;
                part = (WTPart) getLastRevision(part);
                if (part != null)
                    result.putAll(checkVersion(document, part, newVersion));
            }
        }
        return result;
    }

    private Versioned getLastRevision(Versioned part) throws WTException {
        QueryResult allVersions = VersionControlHelper.service.allVersionsOf(part.getMaster(), false);
        HashSet<Integer> allRevisionValue = getAllRevisionValue(allVersions, part);
        VersionIdentifier versionIdentifier = VersionControlHelper.getVersionIdentifier(part);
        Integer value = Integer.valueOf(versionIdentifier.getValue());
        if (value.equals(Collections.max(allRevisionValue))) {
            return part;
        }
        return null;
    }

    private Map getAllEquivalenceTooling(Persistable parent, WTPart part, Object newVersion) throws WTException {
        WTKeyedHashMap result = new WTKeyedHashMap();
        QueryResult equivalence = PersistenceHelper.navigate(part, EquivalenceLink.ROLE_BOBJECT_ROLE, EquivalenceLink.class, true);

        while (equivalence.hasMoreElements()) {
            Object o = equivalence.nextElement();
            if (o instanceof MPMTooling)
                result.putAll(checkVersion(parent, (Persistable) o, newVersion));
        }
        return result;
    }


    private WTKeyedHashMap checkVersion(Persistable parent, Persistable persistable, Object newVersionObj) throws WTException {
        WTKeyedHashMap result = new WTKeyedHashMap();
        QueryResult queryResult = VersionControlHelper.service.allVersionsOf((Versioned) persistable);
        try {
            // TODO: 09.08.2017 check for string value
            Integer validVersion = getValidVersion(queryResult, persistable);
            Integer newVersion = Integer.valueOf(newVersionObj.toString());

            if (newVersion >= validVersion)
                result.put(persistable, newVersion);

            else if (newVersion < validVersion) {
                StringBuilder stringBuilder = new StringBuilder();
                if (parent != null) {
                    stringBuilder
                            .append("(")
                            .append(((RevisionControlled) parent).getDisplayIdentifier().getLocalizedMessage(Locale.getDefault()))
                            .append(")\n ");
                }
                stringBuilder
                        .append("У объекта \n")
                        .append(((RevisionControlled) persistable).getDisplayIdentifier().getLocalizedMessage(Locale.getDefault()))
                        .append("\n нельзя установить версию ниже "+ validVersion);
                throw new WTException(stringBuilder.toString());
            }

        } catch (NumberFormatException e) {
            e.printStackTrace();
        }
        return result;
    }

    private Integer getValidVersion(QueryResult queryResult, Persistable persistable) throws VersionControlException {
        VersionIdentifier versionId = VersionControlHelper.getVersionIdentifier((Versioned) persistable);
        Integer currentVersion = Integer.valueOf(versionId.getValue());
        if (currentVersion == 0)
            return 0;

        HashSet<Integer> set = getAllRevisionValue(queryResult, persistable);
        set.remove(currentVersion);
        if (set.isEmpty()) return 0;
        return Collections.max(set);
    }

    private HashSet<Integer> getAllRevisionValue(QueryResult queryResult, Persistable persistable) throws VersionControlException {
        HashSet<Integer> set = new HashSet();
        ViewReference targetView = persistable instanceof WTPart ? ((WTPart) persistable).getView() : null;

        while (queryResult.hasMoreElements()) {
            Object element = queryResult.nextElement();
            ViewReference elementView = element instanceof WTPart ? ((WTPart) element).getView() : null;
            if (elementView == null || elementView.equals(targetView)) {
                VersionIdentifier versionIdentifier = VersionControlHelper.getVersionIdentifier((Versioned) element);
                set.add(Integer.valueOf(versionIdentifier.getValue()));
            }
        }
        return set;
    }

    private boolean checkValidType(Persistable persistable) {
        try {

            if (validTypes == null) loadValidTypesProperty();

            for (Object o : validTypes.keySet()) {
                boolean isMach = checkSoftType(persistable, o.toString());
                if (isMach)
                    return false;
            }
            return !validTypes.containsKey(persistable);
        } catch (IOException e) {
            e.printStackTrace();
            return true;
        }

    }

    private void loadValidTypesProperty() throws IOException {
        URL resource = this.getClass().getResource("../../../alignversion/exceptDocSoftTypes.properties");
        InputStreamReader readerProp = new InputStreamReader(resource.openStream(), "UTF-8");
        validTypes = new Properties();
        validTypes.load(readerProp);
    }

    private boolean checkSoftType(Persistable object, String softType) {
        TypeIdentifier typeIdentifier = TypeIdentifierHelper.getType(object);
        TypeIdentifier root = TypeIdentifierHelper.getTypeIdentifier(softType);

        return typeIdentifier.isDescendedFrom(root);
    }

}