/*
 * Copyright (c) 2026, Harald Kuhr
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

import com.twelvemonkeys.imageio.metadata.CompoundDirectory;
import com.twelvemonkeys.imageio.metadata.Directory;
import com.twelvemonkeys.imageio.metadata.Entry;
import com.twelvemonkeys.imageio.metadata.tiff.TIFF;
import com.twelvemonkeys.imageio.metadata.tiff.TIFFReader;
import com.twelvemonkeys.imageio.stream.ByteArrayImageInputStream;
import com.twelvemonkeys.imageio.util.ImageWriterAbstractTest;

import org.junit.jupiter.api.Test;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.spi.ImageWriterSpi;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.*;
import java.awt.image.*;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import static com.twelvemonkeys.imageio.util.ImageReaderAbstractTest.assertRGBEquals;
import static org.junit.jupiter.api.Assertions.*;

/**
 * PyramidalTIFFWriterTest
 *
 * @author <a href="mailto:harald.kuhr@gmail.com">Harald Kuhr</a>
 * @author last modified by $Author: haraldk$
 * @version $Id: PyramidalTIFFWriterTest.java,v 1.0 10.07.26 12:00 haraldk Exp$
 */
public class PyramidalTIFFWriterTest extends ImageWriterAbstractTest<PyramidalTIFFWriter> {
    @Override
    protected ImageWriterSpi createProvider() {
        return new PyramidalTIFFImageWriterSpi();
    }

    @Override
    protected List<? extends RenderedImage> getTestData() {
        return Arrays.asList(
                new BufferedImage(300, 200, BufferedImage.TYPE_INT_RGB),
                new BufferedImage(301, 199, BufferedImage.TYPE_INT_ARGB),
                new BufferedImage(299, 201, BufferedImage.TYPE_3BYTE_BGR),
                new BufferedImage(160, 90, BufferedImage.TYPE_4BYTE_ABGR),
                new BufferedImage(90, 160, BufferedImage.TYPE_BYTE_GRAY),
                new BufferedImage(30, 20, BufferedImage.TYPE_USHORT_GRAY),
                new BufferedImage(30, 20, BufferedImage.TYPE_BYTE_BINARY),
                new BufferedImage(30, 20, BufferedImage.TYPE_BYTE_INDEXED)
        );
    }

    @Test
    public void testDefaultParamHasWritePyramidSet() throws IOException {
        TIFFImageWriteParam param = (TIFFImageWriteParam) createWriter().getDefaultWriteParam();
        assertTrue(param.getWritePyramid(), "Default param for pyramidal writer should have WritePyramid set");
    }

    @Test
    public void testGenericMIMELookupPrefersPlainTIFFWriter() {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByMIMEType("image/tiff");
        assertTrue(writers.hasNext(), "No writer for image/tiff");
        assertFalse(writers.next() instanceof PyramidalTIFFWriter,
                    "Generic MIME type lookup should not return the pyramidal writer first");
    }

    @Test
    public void testWritePyramidStructure() throws IOException {
        BufferedImage image = new BufferedImage(1000, 800, BufferedImage.TYPE_3BYTE_BGR);
        fillGradient(image);

        byte[] data = writeSingle(image, null);

        // The levels must not appear as pages, and the main image must be unaffected
        try (ImageInputStream input = new ByteArrayImageInputStream(data)) {
            ImageReader reader = ImageIO.getImageReaders(input).next();
            reader.setInput(input);

            assertEquals(1, reader.getNumImages(true), "Levels should not be part of the main IFD chain");
            assertImageEquals("Main image differs", image, reader.read(0), 0);

            reader.dispose();
        }

        Directory ifd = new TIFFReader().read(new ByteArrayImageInputStream(data));

        // Main image is tiled with the default tile size
        assertEquals(256, (int) longs(ifd.getEntryById(TIFF.TAG_TILE_WIDTH))[0]);
        assertEquals(256, (int) longs(ifd.getEntryById(TIFF.TAG_TILE_HEIGTH))[0]);
        assertEquals(4 * 4, longs(ifd.getEntryById(TIFF.TAG_TILE_OFFSETS)).length);

        // Levels are halved until they fit within a single tile: 1000x800 -> 500x400 -> 250x200
        Directory[] levels = subIFDs(ifd);
        assertEquals(2, levels.length, "Unexpected number of levels");

        assertLevel(levels[0], 500, 400, 256);
        assertEquals(2 * 2, longs(levels[0].getEntryById(TIFF.TAG_TILE_OFFSETS)).length);

        assertLevel(levels[1], 250, 200, 256);
        assertEquals(1, longs(levels[1].getEntryById(TIFF.TAG_TILE_OFFSETS)).length);
    }

    @Test
    public void testWritePyramidLevelData() throws IOException {
        Color color = new Color(0x336699);
        BufferedImage image = createSolidFill(700, 600, color);

        byte[] data = writeSingle(image, null);

        // 700x600 -> 350x300 -> 175x150
        Directory[] levels = subIFDs(new TIFFReader().read(new ByteArrayImageInputStream(data)));
        assertEquals(2, levels.length, "Unexpected number of levels");

        assertLevel(levels[0], 350, 300, 256);
        assertLevel(levels[1], 175, 150, 256);

        // Uncompressed chunky RGB: The first samples of the first tile of each level are the solid color
        try (ImageInputStream input = new ByteArrayImageInputStream(data)) {
            for (Directory level : levels) {
                input.seek(longs(level.getEntryById(TIFF.TAG_TILE_OFFSETS))[0]);

                byte[] rgb = new byte[3];
                input.readFully(rgb);

                assertEquals(color.getRed(), rgb[0] & 0xff, "Red sample differs");
                assertEquals(color.getGreen(), rgb[1] & 0xff, "Green sample differs");
                assertEquals(color.getBlue(), rgb[2] & 0xff, "Blue sample differs");
            }
        }
    }

    @Test
    public void testWritePyramidBigTIFF() throws IOException {
        BufferedImage image = new BufferedImage(1000, 800, BufferedImage.TYPE_3BYTE_BGR);
        fillGradient(image);

        byte[] data = writeSingle(new PyramidalBigTIFFImageWriterSpi(), new IIOImage(image, null, null), null);

        // BigTIFF magic (default byte order is big-endian, "MM")
        assertEquals('M', data[0] & 0xff);
        assertEquals('M', data[1] & 0xff);
        assertEquals(43, (data[2] & 0xff) << 8 | (data[3] & 0xff), "Expected BigTIFF magic (43)");

        Directory ifd = new TIFFReader().read(new ByteArrayImageInputStream(data));

        Directory[] levels = subIFDs(ifd);
        assertEquals(2, levels.length, "Unexpected number of levels");
        assertLevel(levels[0], 500, 400, 256);
        assertLevel(levels[1], 250, 200, 256);
    }

    @Test
    public void testWritePyramidDeflate() throws IOException {
        BufferedImage image = new BufferedImage(700, 600, BufferedImage.TYPE_3BYTE_BGR);
        fillGradient(image);

        ImageWriteParam param = createWriter().getDefaultWriteParam();
        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionType("Deflate");

        byte[] data = writeSingle(image, param);

        Directory ifd = new TIFFReader().read(new ByteArrayImageInputStream(data));
        assertEquals(TIFFExtension.COMPRESSION_DEFLATE, (int) longs(ifd.getEntryById(TIFF.TAG_COMPRESSION))[0]);

        Directory[] levels = subIFDs(ifd);
        assertEquals(2, levels.length, "Unexpected number of levels");

        for (Directory level : levels) {
            assertEquals(TIFFExtension.COMPRESSION_DEFLATE, (int) longs(level.getEntryById(TIFF.TAG_COMPRESSION))[0], "Level compression differs from main image");
        }

        // Deflate is lossless
        assertImageEquals("Main image differs", image, readSingle(data), 0);
    }

    @Test
    public void testWritePyramidJPEG() throws IOException {
        Color color = new Color(0x336699);
        // NOTE: Tile aligned dimensions, to avoid JPEG ringing from tile padding at the image edges
        BufferedImage image = createSolidFill(768, 512, color);

        ImageWriteParam param = createWriter().getDefaultWriteParam();
        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionType("JPEG");
        param.setCompressionQuality(1f);

        byte[] data = writeSingle(image, param);

        Directory ifd = new TIFFReader().read(new ByteArrayImageInputStream(data));
        assertEquals(TIFFExtension.COMPRESSION_JPEG, (int) longs(ifd.getEntryById(TIFF.TAG_COMPRESSION))[0]);

        Directory[] levels = subIFDs(ifd);
        assertEquals(2, levels.length, "Unexpected number of levels");

        for (Directory level : levels) {
            assertEquals(TIFFExtension.COMPRESSION_JPEG, (int) longs(level.getEntryById(TIFF.TAG_COMPRESSION))[0], "Level compression differs from main image");
        }

        // Allow room for JPEG compression
        assertImageEquals("Main image differs", image, readSingle(data), 8);
    }

    @Test
    public void testNoPyramidForSmallImage() throws IOException {
        BufferedImage image = new BufferedImage(200, 150, BufferedImage.TYPE_3BYTE_BGR);
        fillGradient(image);

        byte[] data = writeSingle(image, null);

        Directory ifd = new TIFFReader().read(new ByteArrayImageInputStream(data));
        assertNull(ifd.getEntryById(TIFF.TAG_SUB_IFD), "Images that fit within a single tile should have no levels");

        assertImageEquals("Main image differs", image, readSingle(data), 0);
    }

    @Test
    public void testWritePyramidWithThumbnail() throws IOException {
        BufferedImage image = new BufferedImage(600, 500, BufferedImage.TYPE_3BYTE_BGR);
        fillGradient(image);
        BufferedImage thumbnail = new BufferedImage(60, 50, BufferedImage.TYPE_3BYTE_BGR);
        fillGradient(thumbnail);

        byte[] data = writeSingle(new IIOImage(image, Collections.singletonList(thumbnail), null), null);

        // 600x500 -> 300x250 -> 150x125, thumbnail last
        Directory[] subIFDs = subIFDs(new TIFFReader().read(new ByteArrayImageInputStream(data)));
        assertEquals(3, subIFDs.length, "Expected 2 levels + 1 thumbnail");

        assertLevel(subIFDs[0], 300, 250, 256);
        assertLevel(subIFDs[1], 150, 125, 256);

        // The thumbnail is written as a single uncompressed strip
        Directory thumbIFD = subIFDs[2];
        assertEquals(60, (int) longs(thumbIFD.getEntryById(TIFF.TAG_IMAGE_WIDTH))[0]);
        assertEquals(50, (int) longs(thumbIFD.getEntryById(TIFF.TAG_IMAGE_HEIGHT))[0]);
        assertEquals(1, (int) longs(thumbIFD.getEntryById(TIFF.TAG_SUBFILE_TYPE))[0]);
        assertNotNull(thumbIFD.getEntryById(TIFF.TAG_STRIP_OFFSETS));
    }

    @Test
    public void testWritePyramidExplicitTileSize() throws IOException {
        BufferedImage image = new BufferedImage(600, 400, BufferedImage.TYPE_3BYTE_BGR);
        fillGradient(image);

        ImageWriteParam param = createWriter().getDefaultWriteParam();
        param.setTilingMode(ImageWriteParam.MODE_EXPLICIT);
        param.setTiling(128, 128, 0, 0);

        byte[] data = writeSingle(image, param);

        Directory ifd = new TIFFReader().read(new ByteArrayImageInputStream(data));
        assertEquals(128, (int) longs(ifd.getEntryById(TIFF.TAG_TILE_WIDTH))[0]);

        // 600x400 -> 300x200 -> 150x100 -> 75x50
        Directory[] levels = subIFDs(ifd);
        assertEquals(3, levels.length, "Unexpected number of levels");

        assertLevel(levels[0], 300, 200, 128);
        assertLevel(levels[1], 150, 100, 128);
        assertLevel(levels[2], 75, 50, 128);

        assertImageEquals("Main image differs", image, readSingle(data), 0);
    }

    @Test
    public void testWritePyramidStrips() throws IOException {
        BufferedImage image = new BufferedImage(600, 400, BufferedImage.TYPE_3BYTE_BGR);
        fillGradient(image);

        ImageWriteParam param = createWriter().getDefaultWriteParam();
        param.setTilingMode(ImageWriteParam.MODE_DISABLED);

        byte[] data = writeSingle(image, param);

        Directory ifd = new TIFFReader().read(new ByteArrayImageInputStream(data));
        assertNull(ifd.getEntryById(TIFF.TAG_TILE_OFFSETS), "Expected strips, not tiles");
        assertNotNull(ifd.getEntryById(TIFF.TAG_STRIP_OFFSETS));

        // 600x400 -> 300x200 -> 150x100
        Directory[] levels = subIFDs(ifd);
        assertEquals(2, levels.length, "Unexpected number of levels");

        for (Directory level : levels) {
            assertNull(level.getEntryById(TIFF.TAG_TILE_OFFSETS), "Expected strips, not tiles");
            assertNotNull(level.getEntryById(TIFF.TAG_STRIP_OFFSETS));
            assertEquals(1, (int) longs(level.getEntryById(TIFF.TAG_SUBFILE_TYPE))[0]);
        }

        assertEquals(300, (int) longs(levels[0].getEntryById(TIFF.TAG_IMAGE_WIDTH))[0]);
        assertEquals(150, (int) longs(levels[1].getEntryById(TIFF.TAG_IMAGE_WIDTH))[0]);

        assertImageEquals("Main image differs", image, readSingle(data), 0);
    }

    @Test
    public void testWritePyramidIndexed() throws IOException {
        BufferedImage image = new BufferedImage(600, 400, BufferedImage.TYPE_BYTE_INDEXED);
        fillGradient(image);

        byte[] data = writeSingle(image, null);

        Directory ifd = new TIFFReader().read(new ByteArrayImageInputStream(data));

        Directory[] levels = subIFDs(ifd);
        assertEquals(2, levels.length, "Unexpected number of levels");

        for (Directory level : levels) {
            assertEquals(TIFFBaseline.PHOTOMETRIC_PALETTE, (int) longs(level.getEntryById(TIFF.TAG_PHOTOMETRIC_INTERPRETATION))[0]);
            assertEquals(1, (int) longs(level.getEntryById(TIFF.TAG_SAMPLES_PER_PIXEL))[0]);
            assertNotNull(level.getEntryById(TIFF.TAG_COLOR_MAP), "Missing ColorMap for palette level");
        }

        assertImageEquals("Main image differs", image, readSingle(data), 0);
    }

    @Test
    public void testWriteSequencePyramidPerPage() throws IOException {
        BufferedImage first = new BufferedImage(600, 400, BufferedImage.TYPE_3BYTE_BGR);
        fillGradient(first);
        BufferedImage second = new BufferedImage(500, 300, BufferedImage.TYPE_3BYTE_BGR);
        fillGradient(second);

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();

        try (ImageOutputStream output = ImageIO.createImageOutputStream(bytes)) {
            ImageWriter writer = createWriter();
            writer.setOutput(output);
            writer.prepareWriteSequence(null);
            writer.writeToSequence(new IIOImage(first, null, null), null);
            writer.writeToSequence(new IIOImage(second, null, null), null);
            writer.endWriteSequence();
            writer.dispose();
        }

        byte[] data = bytes.toByteArray();

        try (ImageInputStream input = new ByteArrayImageInputStream(data)) {
            ImageReader reader = ImageIO.getImageReaders(input).next();
            reader.setInput(input);

            assertEquals(2, reader.getNumImages(true), "Levels should not be part of the main IFD chain");
            assertImageEquals("First image differs", first, reader.read(0), 0);
            assertImageEquals("Second image differs", second, reader.read(1), 0);

            reader.dispose();
        }

        CompoundDirectory ifds = (CompoundDirectory) new TIFFReader().read(new ByteArrayImageInputStream(data));
        assertEquals(2, ifds.directoryCount());

        // 600x400 -> 300x200 -> 150x100
        Directory[] firstLevels = subIFDs(ifds.getDirectory(0));
        assertEquals(2, firstLevels.length, "Unexpected number of levels for first page");
        assertLevel(firstLevels[0], 300, 200, 256);

        // 500x300 -> 250x150
        Directory[] secondLevels = subIFDs(ifds.getDirectory(1));
        assertEquals(1, secondLevels.length, "Unexpected number of levels for second page");
        assertLevel(secondLevels[0], 250, 150, 256);
    }

    @Test
    public void testWritePyramidUShort() throws IOException {
        BufferedImage image = new BufferedImage(600, 400, BufferedImage.TYPE_USHORT_GRAY);
        fillGradient(image);

        byte[] data = writeSingle(image, null);

        Directory[] levels = subIFDs(new TIFFReader().read(new ByteArrayImageInputStream(data)));
        assertEquals(2, levels.length, "Unexpected number of levels");

        for (Directory level : levels) {
            assertEquals(16, (int) longs(level.getEntryById(TIFF.TAG_BITS_PER_SAMPLE))[0]);
        }

        assertImageEquals("Main image differs", image, readSingle(data), 0);
    }

    @Test
    public void testWritePyramidPagesLayout() throws IOException {
        BufferedImage image = new BufferedImage(600, 400, BufferedImage.TYPE_3BYTE_BGR);
        fillGradient(image);
        BufferedImage thumbnail = new BufferedImage(60, 40, BufferedImage.TYPE_3BYTE_BGR);
        fillGradient(thumbnail);

        TIFFImageWriteParam param = (TIFFImageWriteParam) createWriter().getDefaultWriteParam();
        param.setPyramidLayout(TIFFImageWriteParam.PyramidLayout.PAGES);

        byte[] data = writeSingle(new IIOImage(image, Collections.singletonList(thumbnail), null), param);

        // Levels are pages in the main IFD chain: 600x400 -> 300x200 -> 150x100
        try (ImageInputStream input = new ByteArrayImageInputStream(data)) {
            ImageReader reader = ImageIO.getImageReaders(input).next();
            reader.setInput(input);

            assertEquals(3, reader.getNumImages(true), "Levels should be pages in the main IFD chain");
            assertImageEquals("Main image differs", image, reader.read(0), 0);
            assertEquals(300, reader.getWidth(1));
            assertEquals(200, reader.getHeight(1));
            assertEquals(150, reader.getWidth(2));
            assertEquals(100, reader.getHeight(2));

            reader.dispose();
        }

        CompoundDirectory ifds = (CompoundDirectory) new TIFFReader().read(new ByteArrayImageInputStream(data));
        assertEquals(3, ifds.directoryCount());

        // Level pages are marked reduced-resolution and tiled
        for (int i = 1; i < ifds.directoryCount(); i++) {
            Directory level = ifds.getDirectory(i);
            assertEquals(1, (int) longs(level.getEntryById(TIFF.TAG_SUBFILE_TYPE))[0], "Level page should be marked reduced-resolution (NewSubfileType = 1)");
            assertEquals(256, (int) longs(level.getEntryById(TIFF.TAG_TILE_WIDTH))[0]);
            assertNull(level.getEntryById(TIFF.TAG_SUB_IFD), "Level pages should have no SubIFDs");
        }

        // The thumbnail stays in a SubIFD (tag 330) of the main page
        Entry subIFDEntry = ifds.getDirectory(0).getEntryById(TIFF.TAG_SUB_IFD);
        assertNotNull(subIFDEntry, "Missing SubIFDs (330) entry for thumbnail");
        assertTrue(subIFDEntry.getValue() instanceof Directory, "Expected a single SubIFD (thumbnail only), was: " + subIFDEntry.getValue().getClass());
        assertEquals(60, (int) longs(((Directory) subIFDEntry.getValue()).getEntryById(TIFF.TAG_IMAGE_WIDTH))[0]);
    }

    @Test
    public void testWriteSequencePyramidPagesLayout() throws IOException {
        BufferedImage first = new BufferedImage(600, 400, BufferedImage.TYPE_3BYTE_BGR);
        fillGradient(first);
        BufferedImage second = new BufferedImage(500, 300, BufferedImage.TYPE_3BYTE_BGR);
        fillGradient(second);

        TIFFImageWriteParam param = (TIFFImageWriteParam) createWriter().getDefaultWriteParam();
        param.setPyramidLayout(TIFFImageWriteParam.PyramidLayout.PAGES);

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();

        try (ImageOutputStream output = ImageIO.createImageOutputStream(bytes)) {
            ImageWriter writer = createWriter();
            writer.setOutput(output);
            writer.prepareWriteSequence(null);
            writer.writeToSequence(new IIOImage(first, null, null), param);
            writer.writeToSequence(new IIOImage(second, null, null), param);
            writer.endWriteSequence();
            writer.dispose();
        }

        byte[] data = bytes.toByteArray();

        // Each image is directly followed by its level pages:
        // 600x400, 300x200, 150x100, 500x300, 250x150
        CompoundDirectory ifds = (CompoundDirectory) new TIFFReader().read(new ByteArrayImageInputStream(data));
        assertEquals(5, ifds.directoryCount());

        int[] expectedWidths = {600, 300, 150, 500, 250};
        for (int i = 0; i < expectedWidths.length; i++) {
            assertEquals(expectedWidths[i], (int) longs(ifds.getDirectory(i).getEntryById(TIFF.TAG_IMAGE_WIDTH))[0], "Page " + i + " width differs");
        }

        // Full-resolution pages are not marked reduced-resolution, level pages are
        assertNull(ifds.getDirectory(0).getEntryById(TIFF.TAG_SUBFILE_TYPE));
        assertEquals(1, (int) longs(ifds.getDirectory(1).getEntryById(TIFF.TAG_SUBFILE_TYPE))[0]);
        assertEquals(1, (int) longs(ifds.getDirectory(2).getEntryById(TIFF.TAG_SUBFILE_TYPE))[0]);
        assertNull(ifds.getDirectory(3).getEntryById(TIFF.TAG_SUBFILE_TYPE));
        assertEquals(1, (int) longs(ifds.getDirectory(4).getEntryById(TIFF.TAG_SUBFILE_TYPE))[0]);

        try (ImageInputStream input = new ByteArrayImageInputStream(data)) {
            ImageReader reader = ImageIO.getImageReaders(input).next();
            reader.setInput(input);

            assertEquals(5, reader.getNumImages(true));
            assertImageEquals("First image differs", first, reader.read(0), 0);
            assertImageEquals("Second image differs", second, reader.read(3), 0);

            reader.dispose();
        }
    }

    @Test
    public void testWritePyramidPagesLayoutBigTIFF() throws IOException {
        BufferedImage image = new BufferedImage(700, 500, BufferedImage.TYPE_3BYTE_BGR);
        fillGradient(image);

        TIFFImageWriteParam param = (TIFFImageWriteParam) createWriter().getDefaultWriteParam();
        param.setPyramidLayout(TIFFImageWriteParam.PyramidLayout.PAGES);

        byte[] data = writeSingle(new PyramidalBigTIFFImageWriterSpi(), new IIOImage(image, null, null), param);

        // BigTIFF magic (default byte order is big-endian, "MM")
        assertEquals(43, (data[2] & 0xff) << 8 | (data[3] & 0xff), "Expected BigTIFF magic (43)");

        // 700x500 -> 350x250 -> 175x125
        CompoundDirectory ifds = (CompoundDirectory) new TIFFReader().read(new ByteArrayImageInputStream(data));
        assertEquals(3, ifds.directoryCount());

        assertEquals(350, (int) longs(ifds.getDirectory(1).getEntryById(TIFF.TAG_IMAGE_WIDTH))[0]);
        assertEquals(1, (int) longs(ifds.getDirectory(1).getEntryById(TIFF.TAG_SUBFILE_TYPE))[0]);
        assertEquals(175, (int) longs(ifds.getDirectory(2).getEntryById(TIFF.TAG_IMAGE_WIDTH))[0]);

        assertImageEquals("Main image differs", image, readSingle(data), 0);
    }

    // Helpers

    private static void assertLevel(final Directory level, final int width, final int height, final int tileSize) {
        assertEquals(width, (int) longs(level.getEntryById(TIFF.TAG_IMAGE_WIDTH))[0], "Level width differs");
        assertEquals(height, (int) longs(level.getEntryById(TIFF.TAG_IMAGE_HEIGHT))[0], "Level height differs");
        assertEquals(1, (int) longs(level.getEntryById(TIFF.TAG_SUBFILE_TYPE))[0], "Level should be marked reduced-resolution (NewSubfileType = 1)");
        assertEquals(tileSize, (int) longs(level.getEntryById(TIFF.TAG_TILE_WIDTH))[0], "Level tile width differs");
        assertEquals(tileSize, (int) longs(level.getEntryById(TIFF.TAG_TILE_HEIGTH))[0], "Level tile height differs");
        assertNotNull(level.getEntryById(TIFF.TAG_TILE_OFFSETS), "Missing TileOffsets for level");
        assertNotNull(level.getEntryById(TIFF.TAG_TILE_BYTE_COUNTS), "Missing TileByteCounts for level");
    }

    private static Directory[] subIFDs(final Directory ifd) {
        Entry subIFDEntry = ifd.getEntryById(TIFF.TAG_SUB_IFD);
        assertNotNull(subIFDEntry, "Missing SubIFDs (330) entry");

        Object value = subIFDEntry.getValue();

        if (value instanceof Directory) {
            return new Directory[] {(Directory) value};
        }

        assertTrue(value instanceof Directory[], "SubIFDs entry should contain parsed sub-IFDs, was: " + value.getClass());

        return (Directory[]) value;
    }

    private byte[] writeSingle(final RenderedImage image, final ImageWriteParam param) throws IOException {
        return writeSingle(provider, new IIOImage(image, null, null), param);
    }

    private byte[] writeSingle(final IIOImage image, final ImageWriteParam param) throws IOException {
        return writeSingle(provider, image, param);
    }

    private byte[] writeSingle(final ImageWriterSpi spi, final IIOImage image, final ImageWriteParam param) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();

        try (ImageOutputStream output = ImageIO.createImageOutputStream(bytes)) {
            ImageWriter writer = spi.createWriterInstance();
            writer.setOutput(output);
            writer.write(null, image, param);
            writer.dispose();
        }

        return bytes.toByteArray();
    }

    private BufferedImage readSingle(final byte[] data) throws IOException {
        try (ImageInputStream input = new ByteArrayImageInputStream(data)) {
            ImageReader reader = ImageIO.getImageReaders(input).next();

            try {
                reader.setInput(input);

                return reader.read(0);
            }
            finally {
                reader.dispose();
            }
        }
    }

    private static long[] longs(final Entry entry) {
        assertNotNull(entry);
        Object value = entry.getValue();

        if (value instanceof Number) {
            return new long[] {((Number) value).longValue()};
        }
        if (value instanceof long[]) {
            return (long[]) value;
        }
        if (value instanceof int[]) {
            int[] ints = (int[]) value;
            long[] longs = new long[ints.length];
            for (int i = 0; i < longs.length; i++) {
                longs[i] = ints[i] & 0xffffffffL;
            }
            return longs;
        }
        if (value instanceof short[]) {
            short[] shorts = (short[]) value;
            long[] longs = new long[shorts.length];
            for (int i = 0; i < longs.length; i++) {
                longs[i] = shorts[i] & 0xffffL;
            }
            return longs;
        }

        throw new AssertionError("Unexpected value type: " + value.getClass());
    }

    private static void fillGradient(final BufferedImage image) {
        WritableRaster raster = image.getRaster();
        int maxSample = (1 << Math.min(16, raster.getSampleModel().getSampleSize(0))) - 1;

        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                for (int b = 0; b < raster.getNumBands(); b++) {
                    raster.setSample(x, y, b, ((x * 7 + y * 13 + b * 29) * 71) % (maxSample + 1));
                }
            }
        }
    }

    private static BufferedImage createSolidFill(final int width, final int height, final Color color) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_3BYTE_BGR);
        Graphics2D g2d = image.createGraphics();
        try {
            g2d.setColor(color);
            g2d.fillRect(0, 0, width, height);
        }
        finally {
            g2d.dispose();
        }

        return image;
    }

    private void assertImageEquals(final String message, final BufferedImage expected, final BufferedImage actual, final int tolerance) {
        assertNotNull(expected, message);
        assertNotNull(actual, message);
        assertEquals(expected.getWidth(), actual.getWidth(), message + ", widths differ");
        assertEquals(expected.getHeight(), actual.getHeight(), message + ", heights differ");

        for (int y = 0; y < expected.getHeight(); y++) {
            for (int x = 0; x < expected.getWidth(); x++) {
                assertRGBEquals(String.format("%s, ARGB differs at (%s,%s)", message, x, y), expected.getRGB(x, y), actual.getRGB(x, y), tolerance);
            }
        }
    }
}
