package ru.pts.base.actions;

import com.ptc.windchill.mpml.explorer.common.actions.OperationResourceAssociationAction;

/**
 * Created by Komyshenets on 08.03.2017.
 */
public class PtsOperationResourceAssociationAction extends OperationResourceAssociationAction {
    @Override
    public boolean isRequestedSilent() {
        return true;
    }
}
