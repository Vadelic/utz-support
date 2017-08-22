package ru.pts.estdex.dataUtilities;

import com.ptc.core.components.descriptor.ModelContext;
import com.ptc.core.components.factory.AbstractDataUtility;
import com.ptc.windchill.enterprise.object.guicomponents.NumericalTextBox;
import wt.util.WTException;
import wt.vc.VersionControlHelper;
import wt.vc.VersionIdentifier;
import wt.vc.Versioned;

/**
 * Created by Komyshenets on 13.04.2017.
 */
public class AlignVersionDataUtility extends AbstractDataUtility {
    @Override
    public Object getDataValue(String componentId, Object o, ModelContext modelContext) throws WTException {
        NumericalTextBox comp = new NumericalTextBox();
        comp.setMaxLength(3);
        VersionIdentifier versionIdentifier = VersionControlHelper.getVersionIdentifier((Versioned) o);
        String value = versionIdentifier.getValue();

        String result;
        try {
            Integer integer = Integer.valueOf(value);
            result = String.valueOf(++integer);
        } catch (NumberFormatException e) {
            result = String.valueOf(0);
        }

        comp.setValue(result);
        return comp;
    }
}
