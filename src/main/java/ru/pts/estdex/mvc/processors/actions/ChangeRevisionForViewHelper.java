package ru.pts.estdex.mvc.processors.actions;

import wt.access.AccessControlHelper;
import wt.access.AccessPermission;
import wt.fc.*;
import wt.fc.collections.WTArrayList;
import wt.fc.collections.WTHashSet;
import wt.identity.IdentityFactory;
import wt.part.WTPart;
import wt.pds.StatementSpec;
import wt.pom.Transaction;
import wt.query.QuerySpec;
import wt.query.SearchCondition;
import wt.series.MultilevelSeries;
import wt.series.SeriesHelper;
import wt.series.SeriesServerHelper;
import wt.session.SessionServerHelper;
import wt.util.WTException;
import wt.util.WTPropertyVetoException;
import wt.vc.*;
import wt.vc.config.*;
import wt.vc.sessioniteration.SessionEditedIteration;
import wt.vc.views.ViewManageable;
import wt.vc.wip.WorkInProgressHelper;
import wt.vc.wip.Workable;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.TreeSet;

/**
 * Created by Komyshenets on 28.09.2017.
 */
class ChangeRevisionForViewHelper {
    static void changeRevision(Versioned versioned, String value, boolean var3) throws WTException, IllegalAccessException, NoSuchMethodException, InvocationTargetException {
        try {
            MultilevelSeries seriesOrig = VersionControlHelper.getVersionIdentifier(versioned).getSeries();
            MultilevelSeries seriesNew = (MultilevelSeries) seriesOrig.clone();
            seriesNew.setValueWithoutValidating(value);
            SeriesHelper.validateInSeriesRange(seriesNew);

            if (seriesOrig.getLevel() <= 1 && seriesNew.getLevel() <= 1) {
                ArrayList<ControlBranch> branchArrayList = new ArrayList();
                TreeSet<MultilevelSeries> seriesTreeSet = new TreeSet(MultilevelSeries.getMulitlevelSeriesComparator());

                QueryResult controlBranches;
                boolean flag = SessionServerHelper.manager.setAccessEnforced(false);
                try {
                    controlBranches = getControlBranchesOf(versioned);
                } finally {
                    SessionServerHelper.manager.setAccessEnforced(flag);
                }

                seriesTreeSet.add(seriesOrig);
                seriesTreeSet.add(seriesNew);
                while (controlBranches != null && controlBranches.hasMoreElements()) {
                    ControlBranch branch = (ControlBranch) controlBranches.nextElement();

                    if (versioned instanceof WTPart) {
                        long viewIdVersioned = ((ObjectIdentifier) ((WTPart) versioned).getView().getKey()).getId();
                        long viewIdBranch = getViewId(branch);
                        if (viewIdBranch != viewIdVersioned)
                            continue;
                    }

                    MultilevelSeries series = (MultilevelSeries) seriesOrig.clone();

                    String versionId = getVersionId(branch);

                    series.setValueWithoutValidating(versionId);
                    SeriesHelper.validateInSeriesRange(series);
                    if (seriesOrig.equals(series)) {
                        branchArrayList.add(branch);
                    }

                    if (seriesNew.equals(series) && !seriesNew.equals(seriesOrig)) {
                        throw new VersionControlException("wt.vc.vcResource", "60", new Object[]{IdentityFactory.getDisplayIdentity(versioned)});
                    }

                    if (series.getLevel() > 1) {
                        throw new VersionControlException("wt.vc.vcResource", "64", null);
                    }

                    seriesTreeSet.add(series);
                }

                if (branchArrayList.size() == 0) {
                    throw new VersionControlException("wt.vc.vcResource", "57", new Object[]{IdentityFactory.getDisplayIdentity(versioned)});
                } else {
                    if (!seriesOrig.equals(seriesNew)) {
                        ArrayList<MultilevelSeries> var44 = new ArrayList(seriesTreeSet);
                        int var46 = var44.indexOf(seriesOrig);
                        int var12 = var44.indexOf(seriesNew);
                        if (Math.abs(var46 - var12) != 1) {
                            throw new VersionControlException("wt.vc.vcResource", "65", new Object[]{IdentityFactory.getDisplayIdentity(versioned), seriesNew.getValue()});
                        }
                    }

                    WTArrayList var45 = new WTArrayList();
                    WTArrayList var47 = new WTArrayList();
                    HashMap<Long, ControlBranch> var48 = new HashMap();
                    long[] var13 = new long[branchArrayList.size()];
                    int var14 = 0;

                    for (ControlBranch var16 : branchArrayList) {
                        var13[var14] = var16.getPersistInfo().getObjectIdentifier().getId();
                        var48.put(var13[var14++], var16);
                    }

                    flag = SessionServerHelper.manager.setAccessEnforced(false);
                    QueryResult var49;
                    try {
                        var49 = getAllIterationsForBranches(versioned.getClass(), var13);
                    } finally {
                        SessionServerHelper.manager.setAccessEnforced(flag);
                    }

                    while (var49.hasMoreElements()) {
                        Iterated localObject4 = (Iterated) var49.nextElement();

                        if ((!var3) && ((localObject4 instanceof Workable)) && (WorkInProgressHelper.isCheckedOut((Workable) localObject4))) {
                            throw new VersionControlException("wt.vc.vcResource", "65", new Object[]{IdentityFactory.getDisplayIdentity(localObject4), seriesNew.getValue()});
                        }

                        if (VersionControlHelper.isLatestIteration(localObject4)) {
                            var47.add(localObject4);
                        }
                        var45.add(localObject4);
                    }


                    AccessControlHelper.manager.checkAccess(var47, AccessPermission.MODIFY);
                    Transaction var51 = new Transaction();

                    try {
                        var51.start();
                        VersionIdentifier var17 = VersionIdentifier.newVersionIdentifier(seriesNew);
                        String var18 = SeriesServerHelper.getSeriesSortId(seriesNew);
                        var17.setVersionSortId(var18);
                        WTHashSet var19 = new WTHashSet();
                        Iterator var20 = var45.persistableIterator();

                        while (var20.hasNext()) {
                            Versioned var21 = (Versioned) var20.next();
                            VersionControlServerHelper.setVersionIdentifier(var21, var17, false);
                            if (VersionControlHelper.isLatestIteration(var21)) {
                                ControlBranch var22 = var48.get(var21.getBranchIdentifier());
                                var22.setUntrustedBusinessFields(var21);
                                var19.add(var22);
                            }
                        }
                        var45.addAll(var19);
                        PersistenceServerHelper.manager.update(var45, true);
                        var51.commit();
                        var51 = null;
                    } finally {
                        if (var51 != null) {
                            var51.rollback();
                        }
                    }
                }
            } else {
                throw new VersionControlException("wt.vc.vcResource", "64", null);
            }
        } catch (CloneNotSupportedException var42) {
            throw new VersionControlException(var42);
        }catch (WTPropertyVetoException var42) {
            throw new VersionControlException(var42);
        }
    }

    private static String getVersionId(ControlBranch branch) throws InvocationTargetException, IllegalAccessException, NoSuchMethodException {
        Method method = ControlBranch.class.getDeclaredMethod("getVersionId");
        method.setAccessible(true);
        Object invoke = method.invoke(branch);
        return (String) invoke;
    }

    private static long getViewId(ControlBranch branch) throws InvocationTargetException, IllegalAccessException, NoSuchMethodException {
        Method method = ControlBranch.class.getDeclaredMethod("getViewId");
        method.setAccessible(true);
        Long invoke = (Long) method.invoke(branch);
        return invoke;
    }

    private static QueryResult getControlBranchesOf(Versioned var1) throws WTException {
        QuerySpec var2 = new QuerySpec(ControlBranch.class);
        var2.appendWhere(VersionControlHelper.getSearchCondition(ControlBranch.class, var1.getMaster()), new int[]{0});
        return PersistenceServerHelper.manager.query(var2);
    }

    private static void addOrderByClauses(Class var0, QuerySpec var1) throws WTException {
        if (ViewManageable.class.isAssignableFrom(var0)) {
            (new ViewManageableOrderByPrimitive()).appendOrderBy(var1, 0, true);
            (new ViewManageableOrderByVariation1Primitive()).appendOrderBy(var1, 0, true);
            (new ViewManageableOrderByVariation2Primitive()).appendOrderBy(var1, 0, true);
        }

        if (Versioned.class.isAssignableFrom(var0)) {
            (new VersionedOrderByPrimitive()).appendOrderBy(var1, 0, true);
        }

        if (OneOffVersioned.class.isAssignableFrom(var0)) {
            (new OneOffVersionedOrderByPrimitive()).appendOrderBy(var1, 0, true);
        }

        if (AdHocStringVersioned.class.isAssignableFrom(var0)) {
            (new AdHocStringVersionedOrderByPrimitive()).appendOrderBy(var1, 0, true);
        }

        if (Iterated.class.isAssignableFrom(var0)) {
            (new IteratedOrderByPrimitive()).appendOrderBy(var1, 0, true);
        }

        if (Workable.class.isAssignableFrom(var0)) {
            (new WorkableOrderByPrimitive()).appendOrderBy(var1, 0, true);
        }

        if (SessionEditedIteration.class.isAssignableFrom(var0)) {
            (new SessionEditedIterationOrderByPrimitive()).appendOrderBy(var1, 0, true);
        }

    }

    private static QuerySpec initQuerySpec(Class var0, boolean var1) throws WTException {
        QuerySpec var2 = new QuerySpec(var0);
        if (!var1) {
            try {
                var2.setDescendantQuery(false);
            } catch (WTPropertyVetoException var4) {
                throw new WTException(var4);
            }
        }

        addOrderByClauses(var0, var2);
        return var2;
    }

    private static QueryResult getAllIterationsForBranches(Class aClass, long[] longs) throws WTException {
        QuerySpec querySpec = initQuerySpec(aClass, Iterated.class == aClass);
        querySpec.appendWhere(new SearchCondition(aClass, "iterationInfo.branchId", longs, false), new int[]{0});
        return find(querySpec);
    }

    private static QueryResult find(StatementSpec statementSpec) throws WTException {
        IteratedObjectVector vector = new IteratedObjectVector();
        QueryResult result = PersistenceHelper.manager.find(statementSpec);

        while (result.hasMoreElements()) {
            vector.addElement(result.nextElement());
        }

        return new QueryResult(vector);
    }
}
