package ru.pts.estdex.mvc.processors.actions;

import com.ptc.core.components.beans.ObjectBean;
import com.ptc.core.components.forms.DefaultObjectFormProcessor;
import com.ptc.core.components.forms.FormResult;
import com.ptc.core.components.util.FeedbackMessage;
import com.ptc.core.meta.common.TypeIdentifier;
import com.ptc.core.meta.common.TypeIdentifierHelper;
import com.ptc.core.ui.resources.FeedbackType;
import com.ptc.netmarkets.model.NmOid;
import com.ptc.netmarkets.util.beans.NmCommandBean;
import com.ptc.netmarkets.util.misc.NmContext;
import com.ptc.windchill.mpml.resource.MPMTooling;
import wt.associativity.EquivalenceLink;
import wt.content.*;
import wt.doc.WTDocument;
import wt.enterprise.RevisionControlled;
import wt.fc.*;
import wt.fc.collections.WTKeyedHashMap;
import wt.fc.collections.WTKeyedMap;
import wt.part.WTPart;
import wt.part.WTPartHelper;
import wt.pom.Transaction;
import wt.util.WTException;
import wt.vc.VersionControlHelper;
import wt.vc.VersionIdentifier;
import wt.vc.Versioned;

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
            for (Object entry : mapTargetObjects.entrySet()) {
                WTKeyedMap.WTEntry wtEntry = (WTKeyedHashMap.WTEntry) entry;
                ObjectReference key = (ObjectReference) wtEntry.getKey();
                VersionControlHelper.service.changeRevision((Versioned) key.getObject(), wtEntry.getValue().toString());
            }

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
                        applicationData.setRole(ContentRoleType.PRIMARY);
                    } else {
                        applicationData = (ApplicationData) primaryContent;
                    }
                    applicationData.setFileName(fileName);
                    ContentServerHelper.service.updateContent((ContentHolder) targetObject, applicationData, new FileInputStream(file));
                }
            }


            FormResult formResult = new FormResult();
            String msg = mapTargetObjects.isEmpty() ? "Нет объектов для изменения" : "Версии изменились";
            FeedbackMessage message = new FeedbackMessage(FeedbackType.SUCCESS, null, null, null, new String[]{msg});
            formResult.addFeedbackMessage(message);
            formResult.setSkipPageRefresh(false);

            transaction.commit();
            return formResult;
        } catch (Exception e) {
            FormResult formResult = new FormResult();
            String msg = "Версии изменить не удалось";
            FeedbackMessage message = new FeedbackMessage(FeedbackType.FAILURE, null, null, null, new String[]{msg});
            formResult.addFeedbackMessage(message);
            formResult.setSkipPageRefresh(true);

            transaction.rollback();
            return formResult;
        }
    }

    private HashMap getNewFiles(NmCommandBean nmCommandBean, NmOid targetOid) throws WTException {
        HashMap result = new HashMap();

        String targetOidOR = targetOid.getOidObject().getStringValue();
        Object map = nmCommandBean.getMap().get("fileUploadMap");
        if (map != null) {
            HashMap fileUploadMap = (HashMap) map;
            Object file = fileUploadMap.get("fileData$" + targetOidOR);
            String name = ((String[]) nmCommandBean.getParameterMap().get("fileData$" + targetOidOR))[0];
            result.put(name, file);
        }
        return result;
    }

    private ArrayList<String> convertToArrayList(Object object) {
        ArrayList<String> result = new ArrayList<String>();
        if (object instanceof String[]) {
            for (String o : (String[]) object) {
                if (!"".equals(o)) {
                    result.add(o);
                }
            }
        }
        return result;
    }

    private WTKeyedHashMap compileTargetObjects(String oid, Object newVersion, boolean addLinkedParts, boolean addLinkedTooling) throws WTException {
        WTKeyedHashMap mapTargetObject = new WTKeyedHashMap();

        if (newVersion == null || "".equals(newVersion)) return mapTargetObject;
        Persistable persistable = new ReferenceFactory().getReference(oid).getObject();
        mapTargetObject.putAll(checkVersion(persistable, newVersion));

        if (addLinkedParts && persistable instanceof WTDocument && checkValidType(persistable)) {
            mapTargetObject.putAll(getAllLinkedParts((WTDocument) persistable, newVersion));
        }
        if (addLinkedTooling) {
            WTKeyedHashMap eqTooling = new WTKeyedHashMap();

            for (Object o : mapTargetObject.keySet()) {
                Persistable object = ((ObjectReference) o).getObject();
                if (object instanceof WTPart) {
                    eqTooling.putAll(getAllEquivalenceTooling((WTPart) object, newVersion));
                }
            }
            mapTargetObject.putAll(eqTooling);
        }
        return mapTargetObject;
    }

    private Map getAllLinkedParts(WTDocument document, Object newVersion) throws WTException {
        WTKeyedHashMap result = new WTKeyedHashMap();
        QueryResult describesWTParts = WTPartHelper.service.getDescribesWTParts(document);
        while (describesWTParts.hasMoreElements()) {
            WTPart o = (WTPart) describesWTParts.nextElement();
            if (o.getClass().getName().contains(WTPart.class.getName()))
                result.putAll(checkVersion(o, newVersion));
        }
        return result;
    }

    private Map getAllEquivalenceTooling(WTPart part, Object newVersion) throws WTException {
        WTKeyedHashMap result = new WTKeyedHashMap();
        QueryResult equivalence = PersistenceHelper.navigate(part, EquivalenceLink.ROLE_BOBJECT_ROLE, EquivalenceLink.class, true);

        while (equivalence.hasMoreElements()) {
            Object o = equivalence.nextElement();
            if (o instanceof MPMTooling)
                result.putAll(checkVersion((Persistable) o, newVersion));
        }
        return result;
    }


    private WTKeyedHashMap checkVersion(Persistable persistable, Object newVersion) throws WTException {
        WTKeyedHashMap result = new WTKeyedHashMap();

        VersionIdentifier versionIdentifier = VersionControlHelper.getVersionIdentifier((Versioned) persistable);
        String value = versionIdentifier.getValue();
        if (Integer.valueOf(newVersion.toString()) > Integer.valueOf(value))
            result.put(persistable, newVersion);
        else if (Integer.valueOf(newVersion.toString()) < Integer.valueOf(value)) {
            throw new WTException("У объекта " + ((RevisionControlled) persistable).getDisplayIdentifier().getLocalizedMessage(Locale.getDefault()) + " текущая версия выше чем желаемая");
        }
        return result;
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

    private boolean checkSoftType(Object object, String softType) {
        TypeIdentifier typeIdentifier = TypeIdentifierHelper.getType(object);
        TypeIdentifier root = TypeIdentifierHelper.getTypeIdentifier(softType);

        return typeIdentifier.isDescendedFrom(root);
    }

}