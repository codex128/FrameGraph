/*
 * Copyright (c) 2024, codex
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * 3. Neither the name of the copyright holder nor the names of its
 *    contributors may be used to endorse or promote products derived from
 *    this software without specific prior written permission.
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
package codex.renthyl.client;

import codex.renthyl.FrameGraph;
import com.jme3.renderer.ViewPort;

/**
 * Provides values to a FrameGraph from game logic.
 * 
 * @author codex
 * @param <T>
 */
public interface GraphSource <T> {
    
    /**
     * Gets the value provided to the framegraph.
     * 
     * @param frameGraph framegraph currently rendering
     * @param viewPort viewport currently being rendered
     * @return value (may be null in some circumstances)
     */
    public T getGraphValue(FrameGraph frameGraph, ViewPort viewPort);
    
    /**
     * Returns the value from provided by the GraphSource.
     * <p>
     * If the source is null, the default value will be returned instead.
     * 
     * @param <T>
     * @param source graph source
     * @param defValue default value (used if graph source is null)
     * @param frameGraph framegraph
     * @param viewPort viewport
     * @return value from source (or default value if source is null)
     */
    public static <T> T get(GraphSource<T> source, T defValue, FrameGraph frameGraph, ViewPort viewPort) {
        if (source != null) {
            return source.getGraphValue(frameGraph, viewPort);
        }
        return defValue;
    }
    
    /**
     * Returns a source that returns the given value.
     * 
     * @param <T>
     * @param value
     * @return 
     */
    public static <T> GraphValue<T> value(T value) {
        return new GraphValue<>(value);
    }
    
}
