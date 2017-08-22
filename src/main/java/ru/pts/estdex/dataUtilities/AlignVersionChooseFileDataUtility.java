package pts.estdex.dataUtilities;

import com.ptc.core.components.descriptor.ModelContext;
import com.ptc.core.components.factory.AbstractDataUtility;
import com.ptc.core.htmlcomp.createdocument.guicomponents.FileInputComponent;
import wt.fc.Persistable;
import wt.util.WTException;

/**
 * Created by Komyshenets on 13.04.2017.
 */
public class AlignVersionChooseFileDataUtility extends AbstractDataUtility {
    @Override
    public Object getDataValue(String componentId, Object o, ModelContext modelContext) throws WTException {
        FileInputComponent chooseFile = new FileInputComponent();
        chooseFile.setInputType("file");
        if (o instanceof Persistable) {
            String stringValue = ((Persistable) o).getPersistInfo().getObjectIdentifier().getStringValue();
            chooseFile.setName("fileData$" + stringValue);
        }
        return chooseFile;
    }
}
