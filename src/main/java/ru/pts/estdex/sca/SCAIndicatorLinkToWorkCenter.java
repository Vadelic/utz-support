package ru.pts.estdex.sca;

import com.ptc.core.command.server.delegate.ServerCommandDelegateUtility;
import com.ptc.core.meta.common.AssociationIdentifier;
import com.ptc.core.meta.common.AttributeIdentifier;
import com.ptc.core.meta.common.TypeInstanceIdentifier;
import com.ptc.core.meta.container.common.AttributeContainer;
import com.ptc.core.meta.container.common.AttributeContainerFunction;
import com.ptc.core.meta.container.common.InvalidFunctionArgumentException;
import com.ptc.core.meta.container.common.ServerAttributeContainerFunction;
import com.ptc.windchill.mpml.MPMCompatibilityLink;
import com.ptc.windchill.mpml.processplan.operation.MPMOperationToConsumableLink;
import com.ptc.windchill.mpml.processplan.operation.MPMOperationToWorkCenterLink;
import com.ptc.windchill.mpml.resource.MPMResourceGroupMaster;
import com.ptc.windchill.mpml.resource.MPMWorkCenter;
import wt.fc.*;
import wt.fc.collections.WTArrayList;
import wt.part.WTPartUsageLink;
import wt.util.WTException;
import wt.vc.Iterated;
import wt.vc.Mastered;
import wt.vc.VersionControlHelper;

import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Created by Komyshenets on 25.11.2016.
 */
public class SCAIndicatorLinkToWorkCenter implements ServerAttributeContainerFunction {
    private static final Logger LOG = Logger.getLogger(SCAIndicatorLinkToWorkCenter.class.getName());

    @Override
    public Object calculate(AttributeContainer attributeContainer, AssociationIdentifier associationIdentifier) throws InvalidFunctionArgumentException {
        try {
            TypeInstanceIdentifier identifier = (TypeInstanceIdentifier) attributeContainer.getIdentifier();
            Persistable persistable = ServerCommandDelegateUtility.refresh(identifier);

            if (persistable instanceof MPMOperationToConsumableLink) {
                MPMOperationToConsumableLink consumableLink = (MPMOperationToConsumableLink) persistable;

                WTReference operationRef = consumableLink.getRoleAObjectRef();
                QueryResult workCenterFromOperation = PersistenceHelper.navigate(operationRef.getObject(), MPMOperationToWorkCenterLink.ROLE_BOBJECT_ROLE, MPMOperationToWorkCenterLink.class, true);
                QueryResult workCenterFromOperationLatest = new QueryResult(getLatestIterations(workCenterFromOperation));

                WTArrayList wtArrayList = new WTArrayList();
                while (workCenterFromOperationLatest.hasMoreElements()) {
                    MPMWorkCenter o = (MPMWorkCenter) workCenterFromOperationLatest.nextElement();
                    QueryResult workCenterFromTooling = PersistenceHelper.navigate(o, MPMCompatibilityLink.ROLE_BOBJECT_ROLE, MPMCompatibilityLink.class, true);

                    wtArrayList.addAll(flipResourceGroupTree(workCenterFromTooling));
                }

                Persistable toolingMaster = consumableLink.getRoleBObjectRef().getObject();

                if (wtArrayList.contains(toolingMaster)) {
                    return String.valueOf("Да");
                } else {
                    return String.valueOf("Нет");
                }
            }
        } catch (WTException e) {
            e.printStackTrace();
        }
        return "error";
    }

    protected WTArrayList flipResourceGroupTree(QueryResult resources) throws WTException {
        WTArrayList wtArrayList = new WTArrayList();
        while (resources.hasMoreElements()) {
            Object o = resources.nextElement();
            if (o instanceof MPMResourceGroupMaster) {
                Iterated latestVersionOfMaster = getLatestVersionOfMaster((Mastered) o);
                if (latestVersionOfMaster == null) {
                    LOG.log(Level.INFO, "getLatestVersionOfMaster is null: " + o.toString());
                    continue;
                }
                QueryResult navigate = PersistenceHelper.navigate(latestVersionOfMaster, WTPartUsageLink.ROLE_BOBJECT_ROLE, WTPartUsageLink.class, true);
                wtArrayList.addAll(flipResourceGroupTree(navigate));
            } else {
                wtArrayList.addElement(o);
            }
        }
        return wtArrayList;
    }

    private static Iterated getLatestVersionOfMaster(Mastered mastered) throws WTException {
        QueryResult queryResult = VersionControlHelper.service.allVersionsOf(mastered);
        if (queryResult.hasMoreElements()) {
            Object element = queryResult.nextElement();
            return VersionControlHelper.getLatestIteration((Iterated) element, true);
        }
        return null;
    }

    protected ObjectVector getLatestIterations(QueryResult queryResult) throws WTException {
        ObjectVector vector = new ObjectVector();
        while (queryResult.hasMoreElements()) {
            Mastered mastered = (Mastered) queryResult.nextElement();
            Iterated latestVersionOfMaster = getLatestVersionOfMaster(mastered);
            if (latestVersionOfMaster != null) vector.addElement(latestVersionOfMaster);
        }
        return vector;
    }


    @Override
    public Object calculate(AttributeContainer attributeContainer) throws InvalidFunctionArgumentException {
        return this.calculate(attributeContainer, null);
    }

    @Override
    public Object[] getArguments() {
        return new Object[0];
    }

    @Override
    public Object getPrimaryArgument() {
        return null;
    }

    @Override
    public Object[] getArgumentsRecursive() {
        return new Object[0];
    }

    @Override
    public AttributeIdentifier[] getArgumentsRecursive(AttributeContainer attributeContainer, AssociationIdentifier associationIdentifier) {
        return new AttributeIdentifier[0];
    }

    @Override
    public AttributeContainerFunction cloneWithNewArguments(HashMap hashMap) throws InvalidFunctionArgumentException {
        return this;
    }

    @Override
    public AttributeContainerFunction valueOf(String s) throws InvalidFunctionArgumentException {
        return this;
    }
}