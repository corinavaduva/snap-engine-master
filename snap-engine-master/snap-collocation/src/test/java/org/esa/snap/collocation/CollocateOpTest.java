/*
 * Copyright (C) 2012 Brockmann Consult GmbH (info@brockmann-consult.de)
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

package org.esa.snap.collocation;

import org.esa.snap.core.datamodel.Band;
import org.esa.snap.core.datamodel.FlagCoding;
import org.esa.snap.core.datamodel.IndexCoding;
import org.esa.snap.core.datamodel.MapGeoCoding;
import org.esa.snap.core.datamodel.Mask;
import org.esa.snap.core.datamodel.Product;
import org.esa.snap.core.datamodel.ProductData;
import org.esa.snap.core.datamodel.TiePointGrid;
import org.esa.snap.core.datamodel.VirtualBand;
import org.esa.snap.core.dataop.maptransf.Datum;
import org.esa.snap.core.dataop.maptransf.MapInfo;
import org.esa.snap.core.dataop.maptransf.MapProjectionRegistry;
import org.junit.Test;

import java.awt.Color;

import static org.junit.Assert.*;

public class CollocateOpTest {

    @Test
    public void testCollocateOutput() {
        final Product masterProduct = createTestProduct1();
        final Product slaveProduct = createTestProduct2();

        CollocateOp op = new CollocateOp();
        op.setParameterDefaultValues();

        // test default settings
        assertEquals("COLLOCATED", op.getTargetProductType());
        assertEquals(true, op.getRenameMasterComponents());
        assertEquals(true, op.getRenameSlaveComponents());
        assertEquals("${ORIGINAL_NAME}_M", op.getMasterComponentPattern());
        assertEquals("${ORIGINAL_NAME}_S", op.getSlaveComponentPattern());
        assertEquals(ResamplingType.NEAREST_NEIGHBOUR, op.getResamplingType());

        op.setMasterProduct(masterProduct);
        op.setSlaveProduct(slaveProduct);

        Product targetProduct = op.getTargetProduct();

        int numMasterBands = masterProduct.getNumBands();
        int numSlaveBands = slaveProduct.getNumBands();
        int numMasterTPGs = masterProduct.getNumTiePointGrids();
        int numSlaveTPGs = slaveProduct.getNumTiePointGrids();
        assertEquals(numMasterBands + numSlaveBands + numSlaveTPGs, targetProduct.getNumBands());
        assertEquals(numMasterTPGs, targetProduct.getNumTiePointGrids());

        assertEquals("radiance_1_M", targetProduct.getBandAt(0).getName());
        assertEquals("radiance_2_M", targetProduct.getBandAt(1).getName());
        assertEquals("l1_flags_M", targetProduct.getBandAt(15).getName());
        assertEquals("l1_class_M", targetProduct.getBandAt(16).getName());

        assertEquals("reflec_1_S", targetProduct.getBandAt(16+1).getName());
        assertEquals("reflec_2_S", targetProduct.getBandAt(16+2).getName());
        assertEquals("l2_flags_S", targetProduct.getBandAt(16+16).getName());
        assertEquals("l2_class_S", targetProduct.getBandAt(16+17).getName());

        assertEquals("latitude_S", targetProduct.getBandAt(16+17+1).getName());
        assertEquals("longitude_S", targetProduct.getBandAt(16+17+2).getName());
        assertEquals("dem_altitude_S", targetProduct.getBandAt(16+17+3).getName());

        assertEquals("!l1_flags_M.INVALID && radiance_1_M > 10", targetProduct.getBandAt(0).getValidMaskExpression());
        assertEquals("!l1_flags_M.INVALID && radiance_1_M > 10", targetProduct.getBandAt(1).getValidMaskExpression());

        assertEquals("!l2_flags_S.INVALID && reflec_1_S > 0.1", targetProduct.getBandAt(16 + 1).getValidMaskExpression());
        assertEquals("!l2_flags_S.INVALID && reflec_1_S > 0.1", targetProduct.getBandAt(16 + 2).getValidMaskExpression());

        assertEquals(1, targetProduct.getMaskGroup().getNodeCount());
        Mask mask = targetProduct.getMaskGroup().get(0);
        assertNotNull(mask);
        assertEquals("bitmask_M", mask.getName());
        assertEquals("radiance_1_M > 10", Mask.BandMathsType.getExpression(mask));
        assertEquals(Color.RED, mask.getImageColor());
        assertEquals(0.5, mask.getImageTransparency(), 0.00001);

        assertEquals(2, targetProduct.getFlagCodingGroup().getNodeCount());
        assertNotNull(targetProduct.getFlagCodingGroup().get("l1_flags_M"));
        assertNotNull(targetProduct.getFlagCodingGroup().get("l2_flags_S"));

        assertEquals(2, targetProduct.getIndexCodingGroup().getNodeCount());
        assertNotNull(targetProduct.getIndexCodingGroup().get("l1_class_M"));
        assertNotNull(targetProduct.getIndexCodingGroup().get("l2_class_S"));

    }

    static float[] wl = new float[]{
            412.6395569f,
            442.5160217f,
            489.8732910f,
            509.8299866f,
            559.7575684f,
            619.7247925f,
            664.7286987f,
            680.9848022f,
            708.4989624f,
            753.5312500f,
            761.7092285f,
            778.5520020f,
            864.8800049f,
            884.8975830f,
            899.9100342f
    };

    public static Product createTestProduct1() {
        final Product product = new Product("MER_RR_1P", "MER_RR_1P", 16, 16);
        for (int i = 0; i < wl.length; i++) {
            Band band = new VirtualBand("radiance_" + (i + 1), ProductData.TYPE_FLOAT32, 16, 16, "X+Y");
            band.setValidPixelExpression("!l1_flags.INVALID && radiance_1 > 10");
            band.setSpectralWavelength(wl[i]);
            band.setSpectralBandIndex(i);
            product.addBand(band);
        }
        addFlagCoding(product, "l1_flags");
        addIndexCoding(product, "l1_class");

        product.addTiePointGrid(createTPG("latitude"));
        product.addTiePointGrid(createTPG("longitude"));
        product.addTiePointGrid(createTPG("dem_altitude"));
        MapInfo mapInfo1 = new MapInfo(MapProjectionRegistry.getProjection("Geographic Lat/Lon"), 0.0f, 0.0f, 0.0f, 0.0f, 0.1f, 0.1f, Datum.WGS_84);
        mapInfo1.setSceneWidth(16);
        mapInfo1.setSceneHeight(16);
        product.setSceneGeoCoding(new MapGeoCoding(mapInfo1));
        product.addMask("bitmask", "radiance_1 > 10", null, Color.RED, 0.5f);
        return product;
    }

    public static Product createTestProduct2() {
        final Product product = new Product("MER_RR_2P", "MER_RR_2P", 16, 16);
        for (int i = 0; i < wl.length; i++) {
            Band band = new VirtualBand("reflec_" + (i + 1), ProductData.TYPE_FLOAT32, 16, 16, "X*Y");
            band.setValidPixelExpression("!l2_flags.INVALID && reflec_1 > 0.1");
            band.setSpectralWavelength(wl[i]);
            band.setSpectralBandIndex(i);
            product.addBand(band);
        }
        addFlagCoding(product, "l2_flags");
        addIndexCoding(product, "l2_class");

        product.addTiePointGrid(createTPG("latitude"));
        product.addTiePointGrid(createTPG("longitude"));
        product.addTiePointGrid(createTPG("dem_altitude"));
        MapInfo mapInfo2 = new MapInfo(MapProjectionRegistry.getProjection("Geographic Lat/Lon"), 0.0f, 0.0f, 0.2f, 0.2f, 0.1f, 0.1f, Datum.WGS_84);
        mapInfo2.setSceneWidth(16);
        mapInfo2.setSceneHeight(16);
        product.setSceneGeoCoding(new MapGeoCoding(mapInfo2));
        return product;
    }

    private static void addIndexCoding(Product product, String indexCodingName) {
        Band classBand = product.addBand(indexCodingName, ProductData.TYPE_UINT16);
        IndexCoding indexCoding = new IndexCoding(indexCodingName);
        indexCoding.addIndex("CLASS_1", 1, "first class");
        indexCoding.addIndex("CLASS_2", 2, "first class");
        classBand.setSampleCoding(indexCoding);
        product.getIndexCodingGroup().add(indexCoding);
    }

    private static void addFlagCoding(Product product, String flagCodingName) {
        Band flagsBand = product.addBand(flagCodingName, ProductData.TYPE_UINT32);
        FlagCoding flagCoding = new FlagCoding(flagCodingName);
        flagCoding.addFlag("INVALID", 1, "invalid description");
        flagsBand.setSampleCoding(flagCoding);
        product.getFlagCodingGroup().add(flagCoding);
    }

    private static TiePointGrid createTPG(String name) {
        return new TiePointGrid(name, 5, 5, 0.5f, 0.5f, 4f, 4f, new float[5 * 5]);
    }
}
