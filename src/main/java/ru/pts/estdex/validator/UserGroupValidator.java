package ru.pts.estdex.validator;

import com.ptc.core.ui.validation.DefaultUIComponentValidator;
import com.ptc.core.ui.validation.UIValidationCriteria;
import com.ptc.core.ui.validation.UIValidationKey;
import com.ptc.core.ui.validation.UIValidationStatus;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import wt.fc.PersistenceHelper;
import wt.fc.QueryResult;
import wt.org.WTGroup;
import wt.org.WTPrincipalReference;
import wt.query.QuerySpec;
import wt.query.SearchCondition;
import wt.util.WTException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.*;
import java.io.IOException;
import java.net.URL;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Created by Komyshenets on 11.05.2017.
 */
public class UserGroupValidator extends DefaultUIComponentValidator {
    private static final Logger LOG = Logger.getLogger(UserGroupValidator.class.getName());

    @Override
    public UIValidationStatus preValidateAction(UIValidationKey uiValidationKey, UIValidationCriteria uiValidationCriteria) {
        try {
            QuerySpec querySpec = new QuerySpec(WTGroup.class);
            Object[] objects = getPropertyValue().toArray();
            if (objects.length == 0) return UIValidationStatus.ENABLED;
            for (int i = 0; i < objects.length; i++) {
                LOG.info("Search group " + objects[i]);
                querySpec.appendWhere(new SearchCondition(WTGroup.class, WTGroup.NAME, SearchCondition.EQUAL, (String) objects[i]), new int[]{0});
                if (i != objects.length - 1) {
                    querySpec.appendOr();
                }
            }
            QueryResult queryResult = PersistenceHelper.manager.find(querySpec);
            WTPrincipalReference user = uiValidationCriteria.getUser();
            LOG.info("Current User " + user.getPrincipal().getIdentity());

//            {
//                int countGroup = 0;
//                while (queryResult.hasMoreElements()) {
//                    countGroup++;
//                    queryResult.nextElement();
//                }
//            }

            while (queryResult.hasMoreElements()) {
                WTGroup group = (WTGroup) queryResult.nextElement();
                LOG.info(" Search in WTGroup: " + group.getName());

                if (group.isMember(user.getPrincipal())) {
                    LOG.info("Found!");
                    return UIValidationStatus.ENABLED;
                }
                LOG.info("Not found!");

            }
        } catch (WTException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (SAXException e) {
            e.printStackTrace();
        } catch (ParserConfigurationException e) {
            e.printStackTrace();
        } catch (XPathExpressionException e) {
            e.printStackTrace();
        }
        return UIValidationStatus.HIDDEN;
    }


    private Set<Object> getPropertyValue() throws IOException, ParserConfigurationException, SAXException, XPathExpressionException {
        Set<Object> result = new HashSet<Object>();
        URL resource = this.getClass().getResource("UserGroupValidator.xml");
        DocumentBuilder documentBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        Document parse = documentBuilder.parse(String.valueOf(resource.getPath()));
        XPath xpath = XPathFactory.newInstance().newXPath();
        XPathExpression expr = xpath.compile("AlignVersionValidGroup/group");
        NodeList nodes = (NodeList) expr.evaluate(parse, XPathConstants.NODESET);
        for (int i = 0; i < nodes.getLength(); i++) {
            result.add(nodes.item(i).getTextContent());
        }
        return result;
    }
}
