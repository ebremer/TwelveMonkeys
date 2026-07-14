/*
 * Copyright (c) 2021, Harald Kuhr
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 *
 * * Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 * * Neither the name of the copyright holder nor the names of its
 *   contributors may be used to endorse or promote products derived from
 *   this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.twelvemonkeys.imageio.plugins.tiff;

import com.twelvemonkeys.imageio.metadata.Directory;
import com.twelvemonkeys.imageio.metadata.Entry;
import com.twelvemonkeys.imageio.metadata.tiff.TIFF;
import com.twelvemonkeys.imageio.metadata.tiff.TIFFReader;
import com.twelvemonkeys.imageio.stream.ByteArrayImageInputStream;
import com.twelvemonkeys.imageio.util.ImageWriterAbstractTest;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.spi.ImageWriterSpi;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.image.BufferedImage;
import java.awt.image.RenderedImage;
import java.awt.image.WritableRaster;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import static com.twelvemonkeys.imageio.util.ImageReaderAbstractTest.assertRGBEquals;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * BigTIFFImageWriterTest
 *
 * @author <a href="mailto:harald.kuhr@gmail.com">Harald Kuhr</a>
 * @author last modified by $Author: haraldk$
 * @version $Id: BigTIFFImageWriterTest.java,v 1.0 19.09.13 13:22 haraldk Exp$
 */
public class BigTIFFImageWriterTest extends ImageWriterAbstractTest<TIFFImageWriter> {
    @Override
    protected ImageWriterSpi createProvider() {
        return new BigTIFFImageWriterSpi();
    }

    @Override
    protected List<? extends RenderedImage> getTestData() {
        return Arrays.asList(
                new BufferedImage(300, 200, BufferedImage.TYPE_INT_RGB),
                new BufferedImage(301, 199, BufferedImage.TYPE_INT_ARGB),
                new BufferedImage(299, 201, BufferedImage.TYPE_3BYTE_BGR),
                new BufferedImage(160, 90, BufferedImage.TYPE_4BYTE_ABGR),
                new BufferedImage(90, 160, BufferedImage.TYPE_BYTE_GRAY),
                new BufferedImage(30, 20, BufferedImage.TYPE_USHORT_GRAY)
        );
    }

    @Test
    public void roundrtip() throws IOException {
        TIFFImageWriter writer = createWriter();
        ImageReader reader = ImageIO.getImageReader(writer);

        try (ImageInputStream input = ImageIO.createImageInputStream(getClassLoaderResource("/bigtiff/BigTIFF.tif"))) {
            reader.setInput(input);
            BufferedImage image = reader.read(0);

            ByteArrayOutputStream temp = new ByteArrayOutputStream();
            try (ImageOutputStream output = ImageIO.createImageOutputStream(temp)) {
                writer.setOutput(output);
                writer.write(image);
            }
            finally {
                writer.dispose();
            }

            // Validate we actually write BigTIFF
            byte[] data = temp.toByteArray();
            assertArrayEquals(new byte[] { 'M', 'M', 0, TIFF.BIGTIFF_MAGIC}, Arrays.copyOf(data, 4));

            // Read image back and see that it is the same
            try (ImageInputStream stream = new ByteArrayImageInputStream(data)) {
                reader.setInput(stream);
                BufferedImage after = reader.read(0);

                for (int y = 0; y < image.getHeight(); y++) {
                    for (int x = 0; x < image.getWidth(); x++) {
                        assertRGBEquals("Pixel values differ: ", image.getRGB(x, y), after.getRGB(x, y), 0);
                    }
                }
            }
        }
        finally {
            reader.dispose();
        }
    }

    @Test
    public void testWriteThumbnail() throws IOException {
        // NOTE: For BigTIFF, the SubIFDs (330) entry holds 8 byte (LONG8) offsets
        BufferedImage image = new BufferedImage(90, 60, BufferedImage.TYPE_3BYTE_BGR);
        BufferedImage thumbnail = new BufferedImage(9, 6, BufferedImage.TYPE_3BYTE_BGR);

        WritableRaster raster = thumbnail.getRaster();
        for (int y = 0; y < thumbnail.getHeight(); y++) {
            for (int x = 0; x < thumbnail.getWidth(); x++) {
                for (int b = 0; b < raster.getNumBands(); b++) {
                    raster.setSample(x, y, b, (x * 7 + y * 13 + b * 29) * 71 % 256);
                }
            }
        }

        TIFFImageWriter writer = createWriter();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();

        try (ImageOutputStream output = ImageIO.createImageOutputStream(bytes)) {
            writer.setOutput(output);
            writer.write(null, new IIOImage(image, Arrays.asList(thumbnail), null), null);
        }
        finally {
            writer.dispose();
        }

        byte[] data = bytes.toByteArray();
        assertArrayEquals(new byte[] {'M', 'M', 0, TIFF.BIGTIFF_MAGIC}, Arrays.copyOf(data, 4), "Expected BigTIFF output");

        Directory ifd = new TIFFReader().read(new ByteArrayImageInputStream(data));
        Entry subIFDEntry = ifd.getEntryById(TIFF.TAG_SUB_IFD);
        assertNotNull(subIFDEntry, "Missing SubIFDs (330) entry");
        assertTrue(subIFDEntry.getValue() instanceof Directory, "SubIFDs entry should hold a parsed sub-IFD");

        Directory thumbnailIFD = (Directory) subIFDEntry.getValue();
        assertEquals(9, ((Number) thumbnailIFD.getEntryById(TIFF.TAG_IMAGE_WIDTH).getValue()).intValue());
        assertEquals(6, ((Number) thumbnailIFD.getEntryById(TIFF.TAG_IMAGE_HEIGHT).getValue()).intValue());
        assertEquals(1, ((Number) thumbnailIFD.getEntryById(TIFF.TAG_SUBFILE_TYPE).getValue()).intValue());

        // Verify the thumbnail data, uncompressed, chunky, single strip
        int offset = ((Number) thumbnailIFD.getEntryById(TIFF.TAG_STRIP_OFFSETS).getValue()).intValue();
        assertEquals(9L * 6 * 3, ((Number) thumbnailIFD.getEntryById(TIFF.TAG_STRIP_BYTE_COUNTS).getValue()).longValue());

        for (int y = 0, i = offset; y < 6; y++) {
            for (int x = 0; x < 9; x++) {
                for (int b = 0; b < 3; b++, i++) {
                    assertEquals(raster.getSample(x, y, b), data[i] & 0xff,
                                 String.format("Thumbnail sample at (%d,%d) band %d differs", x, y, b));
                }
            }
        }
    }
}
