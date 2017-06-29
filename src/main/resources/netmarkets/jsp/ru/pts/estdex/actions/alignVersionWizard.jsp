<%@ page contentType="text/html; charset=UTF-8" %>
<%@ include file="/netmarkets/jsp/components/beginWizard.jspf" %>
<%@ include file="/netmarkets/jsp/components/includeWizBean.jspf" %>
<%@ taglib prefix="jca" uri="http://www.ptc.com/windchill/taglib/components" %>
<jca:wizard buttonList="DefaultWizardButtons" title="Выравниватель версий">
    <jca:wizardStep action="pts-table-promotion-objects" type="pts" label="Выравниватель версий" />
</jca:wizard>
<%@ include file="/netmarkets/jsp/util/end.jspf" %>