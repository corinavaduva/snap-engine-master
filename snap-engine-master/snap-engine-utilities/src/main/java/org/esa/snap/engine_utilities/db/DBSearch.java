/*
 * Copyright (C) 2014 by Array Systems Computing Inc. http://www.array.ca
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation; either version 3 of the License, or (at your option)
 * any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, see http://www.gnu.org/licenses/
 */
package org.esa.snap.engine_utilities.db;

import org.esa.snap.core.datamodel.GeoPos;
import org.esa.snap.core.datamodel.PixelPos;
import org.esa.snap.core.datamodel.Product;
import org.esa.snap.core.datamodel.ProductData;
import org.esa.snap.engine_utilities.datamodel.AbstractMetadata;

import java.io.File;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**

 */
public class DBSearch {

    public static ProductEntry[] search(final File srcFile) throws Exception {

        return search(CommonReaders.readProduct(srcFile));
    }

    public static ProductEntry[] search(final Product srcProduct) throws Exception {

        final GeoPos centerGeoPos = srcProduct.getSceneGeoCoding().getGeoPos(
                new PixelPos(srcProduct.getSceneRasterWidth() / 2, srcProduct.getSceneRasterHeight() / 2), null);
        final ProductEntry masterEntry = new ProductEntry(srcProduct);
        return findCCDPairs(ProductDB.instance(), masterEntry, centerGeoPos, 1, false);
    }

    private static ProductEntry[] findCCDPairs(final ProductDB db, final ProductEntry master, final GeoPos centerGeoPos,
                                               final int maxSlaves, final boolean anyDate) throws SQLException {

        final DBQuery dbQuery = new DBQuery();
        dbQuery.setFreeQuery(AbstractMetadata.PRODUCT + " <> '" + master.getName() + '\'');
        dbQuery.setSelectionRect(new GeoPos[]{centerGeoPos, centerGeoPos, centerGeoPos, centerGeoPos});
        dbQuery.setReturnAllIfNoIntersection(false);
        dbQuery.setSelectedPass(master.getPass());
        dbQuery.setStartEndDate(null, master.getFirstLineTime().getAsCalendar());
        dbQuery.setSelectedProductTypes(new String[]{master.getProductType()});

        final ProductEntry[] entries = dbQuery.queryDatabase(db);
        if (entries.length == 0)
            return entries;
        return getClosestDatePairs(entries, master, dbQuery, maxSlaves, anyDate);
    }

    private static ProductEntry[] getClosestDatePairs(final ProductEntry[] entries,
                                                      final ProductEntry master, DBQuery dbQuery,
                                                      final int maxSlaves, final boolean anyDate) {
        final double masterTime = master.getFirstLineTime().getMJD();
        double cutoffTime = masterTime;
        if (dbQuery != null && dbQuery.getEndDate() != null) {
            final double endTime = ProductData.UTC.create(dbQuery.getEndDate().getTime(), 0).getMJD();
            if (endTime > masterTime)
                cutoffTime = endTime;
        }

        final List<ProductEntry> resultList = new ArrayList<>(maxSlaves);
        final Map<Double, ProductEntry> timesMap = new HashMap<>();
        final List<Double> diffList = new ArrayList<>();
        // find all before masterTime
        for (ProductEntry entry : entries) {
            final double entryTime = entry.getFirstLineTime().getMJD();
            if (anyDate || entryTime < cutoffTime) {
                final double diff = masterTime - entryTime;
                if (diff > 0 && diff > 1) {
                    timesMap.put(diff, entry);
                    diffList.add(diff);
                }
            }
        }
        Collections.sort(diffList);
        // select only the closest up to maxPairs
        for (Double diff : diffList) {
            resultList.add(timesMap.get(diff));
            if (resultList.size() >= maxSlaves)
                break;
        }

        return resultList.toArray(new ProductEntry[resultList.size()]);
    }
}
