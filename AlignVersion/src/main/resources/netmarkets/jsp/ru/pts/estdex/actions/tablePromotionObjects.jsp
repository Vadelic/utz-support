<%@page language='java' contentType="text/html; charset=UTF-8" %>
<%@ taglib uri="http://www.ptc.com/windchill/taglib/mvc" prefix="mvc" %>
<input name="up-all-linked-parts" id="up-all-linked-parts" type="checkbox" checked />

<script>
    function changePref() {
        var eqToolCB = document.getElementById('up-all-eq-tools');
        var eqPartsCB = document.getElementById('up-all-linked-parts');

        eqToolCB.checked = eqPartsCB.checked;
        eqToolCB.disabled = !eqPartsCB.checked;
    }
</script>
<span style="padding-left: 3px"> Поднимать версии у связанных с документом частями</span><br>

<input name="up-all-eq-tools" id="up-all-eq-tools" type="checkbox" checked/>
<span style="padding-left: 3px">Поднимать версии у связанных с документом инструментами.</span><br>

<%@ include file="/netmarkets/jsp/util/begin.jspf" %>
<jsp:include page="${mvc:getComponentURL('ru.pts.estdex.mvc.builders.AlignVersionForm')}"/>
<%@ include file="/netmarkets/jsp/util/end.jspf" %>