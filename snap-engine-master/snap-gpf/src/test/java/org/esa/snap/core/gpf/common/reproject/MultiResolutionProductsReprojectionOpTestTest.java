package org.esa.snap.core.gpf.common.reproject;

import org.esa.snap.core.datamodel.Band;
import org.esa.snap.core.datamodel.GeoPos;
import org.esa.snap.core.datamodel.PixelPos;
import org.esa.snap.core.datamodel.Product;
import org.esa.snap.core.datamodel.ProductData;
import org.esa.snap.core.datamodel.TiePointGeoCoding;
import org.esa.snap.core.datamodel.TiePointGrid;
import org.junit.Test;

import java.awt.geom.AffineTransform;
import java.awt.geom.NoninvertibleTransformException;

import static junit.framework.Assert.assertEquals;
import static junit.framework.TestCase.assertNotNull;

public class MultiResolutionProductsReprojectionOpTestTest extends AbstractReprojectionOpTest {

    @Test
    public void testReprojectionOfMultiResolutionProduct() throws NoninvertibleTransformException {
        final TiePointGrid latGrid = sourceProduct.getTiePointGrid("latGrid");
        final TiePointGrid lonGrid = sourceProduct.getTiePointGrid("lonGrid");

        final Band bandOfOtherSize = new Band("otherSize", ProductData.TYPE_INT8, 25, 25);
        final AffineTransform otherTransform = new AffineTransform();
        otherTransform.scale(0.5, 0.5);
        final ScaledTiePointGeoCoding otherGeoCoding =
                new ScaledTiePointGeoCoding(latGrid, lonGrid, otherTransform);
        bandOfOtherSize.setGeoCoding(otherGeoCoding);
        sourceProduct.addBand(bandOfOtherSize);

        final Band bandOfYetAnotherSize = new Band("yetAnotherSize", ProductData.TYPE_INT8, 75, 25);
        final AffineTransform yetAnotherTransform = new AffineTransform();
        yetAnotherTransform.scale(1.5, 0.5);
        final ScaledTiePointGeoCoding yetAnotherGeoCoding =
                new ScaledTiePointGeoCoding(latGrid, lonGrid, yetAnotherTransform);
        bandOfYetAnotherSize.setGeoCoding(yetAnotherGeoCoding);
        sourceProduct.addBand(bandOfYetAnotherSize);

        parameterMap.put("wktFile", wktFile);
        final Product reprojectedProduct = createReprojectedProduct();

        assertNotNull(reprojectedProduct);
        assertEquals(6, reprojectedProduct.getNumBands());

        assertEquals(true, reprojectedProduct.containsBand(FLOAT_BAND_NAME));
        final Band reprojectedFloatBand = reprojectedProduct.getBand(FLOAT_BAND_NAME);
        assertEquals(50, reprojectedFloatBand.getSceneRasterWidth());
        assertEquals(50, reprojectedFloatBand.getSceneRasterHeight());

        assertEquals(true, reprojectedProduct.containsBand(INT_BAND_NAME));
        final Band reprojectedIntBand = reprojectedProduct.getBand(INT_BAND_NAME);
        assertEquals(50, reprojectedIntBand.getSceneRasterWidth());
        assertEquals(50, reprojectedIntBand.getSceneRasterHeight());

        assertEquals(true, reprojectedProduct.containsBand("otherSize"));
        final Band reprojectedBandOfOtherSize = reprojectedProduct.getBand("otherSize");
        assertEquals(26, reprojectedBandOfOtherSize.getSceneRasterWidth());
        assertEquals(26, reprojectedBandOfOtherSize.getSceneRasterHeight());

        assertEquals(true, reprojectedProduct.containsBand("yetAnotherSize"));
        final Band reprojectedBandOfYetAnotherSize = reprojectedProduct.getBand("yetAnotherSize");
        assertEquals(93, reprojectedBandOfYetAnotherSize.getSceneRasterWidth());
        assertEquals(26, reprojectedBandOfYetAnotherSize.getSceneRasterHeight());
    }

    private class ScaledTiePointGeoCoding extends TiePointGeoCoding {

        private final AffineTransform transform;
        private final AffineTransform inverse;

        public ScaledTiePointGeoCoding(TiePointGrid latGrid, TiePointGrid lonGrid, AffineTransform transform)
                throws NoninvertibleTransformException {
            super(latGrid, lonGrid);
            this.transform = transform;
            inverse = transform.createInverse();
        }

        @Override
        public GeoPos getGeoPos(PixelPos pixelPos, GeoPos geoPos) {
            PixelPos transformedPixelPos = new PixelPos();
            transform.transform(pixelPos, transformedPixelPos);
            return super.getGeoPos(transformedPixelPos, geoPos);
        }

        @Override
        public PixelPos getPixelPos(GeoPos geoPos, PixelPos pixelPos) {
            pixelPos = super.getPixelPos(geoPos, pixelPos);
            PixelPos transformedPixelPos = new PixelPos();
            inverse.transform(pixelPos, transformedPixelPos);
            pixelPos.setLocation(transformedPixelPos);
            return transformedPixelPos;
        }
    }

} 