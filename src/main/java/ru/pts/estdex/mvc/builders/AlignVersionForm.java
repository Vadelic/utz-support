package ru.pts.estdex.mvc.builders;

import com.ptc.core.components.descriptor.DescriptorConstants;
import com.ptc.core.meta.common.TypeIdentifier;
import com.ptc.core.meta.common.TypeIdentifierHelper;
import com.ptc.jca.mvc.components.JcaComponentParams;
import com.ptc.mvc.components.*;
import com.ptc.netmarkets.util.beans.NmHelperBean;
import wt.enterprise.RevisionControlled;
import wt.fc.Persistable;
import wt.fc.ReferenceFactory;
import wt.fc.collections.WTHashSet;
import wt.part.WTPart;
import wt.util.WTException;

import javax.servlet.ServletRequest;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * by Komyshenets on 30.02.2017.
 */
@ComponentBuilder({"ru.pts.estdex.mvc.builders.AlignVersionForm"})
public class AlignVersionForm extends AbstractComponentBuilder {
    Properties validTypes = null;

    @Override
    public ComponentConfig buildComponentConfig(ComponentParams componentParams) throws WTException {

        final ComponentConfigFactory factory = getComponentConfigFactory();
        final TableConfig table;
        {
            table = factory.newTableConfig();
            table.setLabel("Объекты для выравнивания");
            table.setType(RevisionControlled.class.getName());
            table.setSelectable(true);
            table.setSingleSelect(false);
            table.setShowCount(true);

            table.addComponent(getColumn(DescriptorConstants.ColumnIdentifiers.GENERAL_STATUS_FAMILY, factory));
            table.addComponent(getColumn(DescriptorConstants.ColumnIdentifiers.SHARE_STATUS_FAMILY, factory));
            table.addComponent(getColumn(DescriptorConstants.ColumnIdentifiers.CHANGE_STATUS_FAMILY, factory));
            table.addComponent(getColumn(DescriptorConstants.ColumnIdentifiers.ICON, factory));

            ColumnConfig columnNumber = getColumn(DescriptorConstants.ColumnIdentifiers.NUMBER, factory);
            columnNumber.setInfoPageLink(true);
            table.addComponent(columnNumber);
            table.addComponent(getColumn(DescriptorConstants.ColumnIdentifiers.INFO_ACTION, factory));
            table.addComponent(getColumn(DescriptorConstants.ColumnIdentifiers.VERSION, factory));
            table.addComponent(getColumn(DescriptorConstants.ColumnIdentifiers.NAME, factory));
            table.addComponent(getColumn(DescriptorConstants.ColumnIdentifiers.STATE, factory));
            table.addComponent(getColumn(DescriptorConstants.ColumnIdentifiers.LAST_MODIFIED, factory));

            ColumnConfig newVersion = factory.newColumnConfig("newVersion", true);
            newVersion.setLabel("Новая версия");
            newVersion.setAutoSize(true);
            table.addComponent(newVersion);

            ColumnConfig chooseFile = factory.newColumnConfig("chooseFile", true);
            chooseFile.setLabel("Новое содержимое");
            chooseFile.setAutoSize(true);
            chooseFile.setInfoPageLink(true);
            chooseFile.setDataUtilityId("chooseFile");
            table.addComponent(chooseFile);
        }
        return table;
    }

    private ColumnConfig getColumn(String nameColumn, ComponentConfigFactory factory) {
        ColumnConfig columnConfig = factory.newColumnConfig(nameColumn, true);
        columnConfig.setAutoSize(true);
        columnConfig.setColumnWrapped(false);
        return columnConfig;
    }

    @Override
    public Object buildComponentData(ComponentConfig componentConfig, ComponentParams componentParams) throws Exception {

        NmHelperBean helperBean = ((JcaComponentParams) componentParams).getHelperBean();
        ServletRequest request = helperBean.getRequest();
        String[] soids = (String[]) request.getParameterMap().get("soid");

        WTHashSet wtHashSet = new WTHashSet();

        for (String oid : soids) {
            Pattern pattern = Pattern.compile("([^$]+)!\\*$");
            Matcher matcher = pattern.matcher(oid);
            if (matcher.find()) {
                int i = matcher.groupCount();
                String group = matcher.group(i);
                Persistable persistable = new ReferenceFactory().getReference(group).getObject();
                if (validType(persistable)&& !(persistable instanceof WTPart)) {
                    wtHashSet.add(persistable);
                } else {
                    System.out.println("AlignVersion - " + persistable.getClass() + "include in file exceptClasses.properties");
                }
            }
        }
        return wtHashSet;
    }

    private boolean validType(Persistable persistable) {
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
        URL resource = this.getClass().getResource("../../alignversion/exceptClasses.properties");
        InputStreamReader readerProp = new InputStreamReader(resource.openStream(), "UTF-8");
        validTypes = new Properties();
        validTypes.load(readerProp);
    }

    boolean checkSoftType(Object object, String softType) {
        TypeIdentifier typeIdentifier = TypeIdentifierHelper.getType(object);
        TypeIdentifier root = TypeIdentifierHelper.getTypeIdentifier(softType);

        return typeIdentifier.isDescendedFrom(root);
    }

}
