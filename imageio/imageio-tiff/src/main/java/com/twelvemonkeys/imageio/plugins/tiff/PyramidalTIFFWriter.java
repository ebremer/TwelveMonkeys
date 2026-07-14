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

import javax.imageio.ImageWriteParam;
import javax.imageio.spi.ImageWriterSpi;

/**
 * PyramidalTIFFWriter
 * <p>
 * A convenience writer that always writes standard pyramidal (multi-resolution) TIFFs:
 * The full-resolution image is written as the main IFD, and successively halved reduced-resolution
 * levels are written as SubIFDs (tag 330), marked with NewSubfileType 1 (reduced-resolution image),
 * as in TIFF/EP and DNG.
 * </p>
 * <p>
 * Writing with this writer is equivalent to writing with {@link TIFFImageWriter} using
 * {@link TIFFImageWriteParam#setWritePyramid(boolean) TIFFImageWriteParam.setWritePyramid(true)}
 * (see that method for how levels and tiling are determined), except this writer writes pyramids
 * regardless of the param passed.
 * </p>
 * <p>
 * Usage:
 * </p>
 * <pre><code>
 *     ImageWriter writer = ImageIO.getImageWritersByFormatName("pyramidal-tiff").next(); // or "pyramidal-bigtiff"
 *     writer.setOutput(output);
 *
 *     ImageWriteParam param = writer.getDefaultWriteParam();
 *     param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
 *     param.setCompressionType("Deflate"); // or "JPEG", "LZW", etc.
 *
 *     writer.write(null, new IIOImage(image, null, null), param);
 * </code></pre>
 *
 * @author <a href="mailto:harald.kuhr@gmail.com">Harald Kuhr</a>
 * @author last modified by $Author: haraldk$
 * @version $Id: PyramidalTIFFWriter.java,v 1.0 10.07.26 12:00 haraldk Exp$
 *
 * @see TIFFImageWriteParam#setWritePyramid(boolean)
 * @see TIFFImageWriter
 */
public final class PyramidalTIFFWriter extends TIFFImageWriter {
    PyramidalTIFFWriter(final ImageWriterSpi provider) {
        super(provider);
    }

    @Override
    public ImageWriteParam getDefaultWriteParam() {
        TIFFImageWriteParam param = new TIFFImageWriteParam();
        param.setWritePyramid(true);

        return param;
    }

    @Override
    boolean writesPyramid(final ImageWriteParam param) {
        // This writer always writes pyramids, regardless of param
        return true;
    }
}
