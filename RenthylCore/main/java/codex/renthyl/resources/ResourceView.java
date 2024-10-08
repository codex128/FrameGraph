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
package codex.renthyl.resources;

import codex.renthyl.modules.ModuleIndex;
import codex.renthyl.resources.RenderObject;
import codex.renthyl.definitions.ResourceDef;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Represents an existing or future resource used for rendering.
 * <p>
 * Unlike {@link RenderObject}, this is only loosely connected to a raw
 * rendering resource. In fact, the creation of a ResourceView does not
 * necessarily predict the creation of a raw resource, due to culling.
 * 
 * @author codex
 * @param <T>
 */
public class ResourceView <T> {
    
    private final ResourceUser producer;
    private final ResourceDef<T> def;
    private final ResourceTicket<T> ticket;
    private final TimeFrame lifetime;
    private RenderObject object;
    private T resource;
    private int refs = 0;
    private boolean temporary = false;
    private boolean undefined = false;
    private final AtomicBoolean released = new AtomicBoolean(false);
    
    /**
     * 
     * @param producer
     * @param def
     * @param ticket 
     */
    public ResourceView(ResourceUser producer, ResourceDef<T> def, ResourceTicket<T> ticket) {
        this.producer = producer;
        this.def = def;
        this.ticket = ticket;
        this.lifetime = new TimeFrame(this.producer.getIndex(), 0);
    }
    
    /**
     * Reference this resource from the specified render pass index.
     * 
     * @param index 
     */
    public void reference(ModuleIndex index) {
        lifetime.extendTo(index);
        refs++;
    }
    /**
     * Releases this resource from one user.
     * 
     * @return true if this resource is used after the release
     */
    public boolean release() {
        refs--;
        released.set(true);
        return refs >= 0;
    }
    /**
     * Claims the resource for reading.
     * 
     * @return 
     */
    public boolean claimReadPermissions() {
        return ((def == null || def.isReadConcurrent()) && released.get()) || released.getAndSet(false);
    }
    
    /**
     * Merges the entry view's references and lifetime into this view.
     * 
     * @param entry 
     */
    public void merge(ResourceView<T> entry) {
        refs += entry.refs+1;
        lifetime.merge(entry.lifetime);
        entry.refs = -1;
    }
    
    /**
     * Sets the render object held by this resource.
     * 
     * @param object 
     */
    public void setObject(RenderObject<T> object) {
        if (object != null) {
            setObject(object, object.getObject());
        } else {
            if (this.object != null) {
                this.object.release();
            }
            this.object = null;
            resource = null;
        }
    }
    /**
     * Sets the render object and concrete resource held by this render resource.
     * 
     * @param object
     * @param resource 
     */
    public void setObject(RenderObject object, T resource) {
        Objects.requireNonNull(object, "Object cannot be null.");
        Objects.requireNonNull(resource, "Object resource cannot be null.");
        if (undefined) {
            throw new IllegalStateException("Resource is already undefined.");
        }
        if (object.isAcquired()) {
            throw new IllegalStateException("Object is already acquired.");
        }
        this.object = object;
        this.resource = resource;
        this.object.acquire();
        ticket.setObjectId(this.object.getId());
    }
    /**
     * Directly sets the raw resource held by this render resource.
     * 
     * @param resource 
     */
    public void setPrimitive(T resource) {
        if (undefined) {
            throw new IllegalStateException("Resource is already marked as undefined.");
        }
        object = null;
        this.resource = resource;
    }
    /**
     * Marks this resource as undefined.
     */
    public void setUndefined() {
        if (resource != null) {
            throw new IllegalStateException("Resource is already defined.");
        }
        undefined = true;
    }
    /**
     * Returns true if this resource always survives cull by reference.
     * 
     * @param temporary 
     */
    public void setTemporary(boolean temporary) {
        this.temporary = temporary;
    }
    
    /**
     * Gets this resource's producer.
     * 
     * @return 
     */
    public ResourceUser getProducer() {
        return producer;
    }
    /**
     * Gets the resource definition.
     * 
     * @return 
     */
    public ResourceDef<T> getDefinition() {
        return def;
    }
    /**
     * Gets the resource ticket.
     * 
     * @return 
     */
    public ResourceTicket<T> getTicket() {
        return ticket;
    }
    /**
     * Gets the lifetime of this resource in render pass indices.
     * 
     * @return 
     */
    public TimeFrame getLifeTime() {
        return lifetime;
    }
    /**
     * Gets the render object.
     * 
     * @return 
     */
    public RenderObject getObject() {
        return object;
    }
    /**
     * Gets the concrete resource.
     * 
     * @return 
     */
    public T getResource() {
        return resource;
    }
    /**
     * Gets the index of this resource.
     * 
     * @return 
     */
    public int getIndex() {
        return ticket.getWorldIndex();
    }
    /**
     * Gets the number of references to this resource.
     * 
     * @return 
     */
    public int getNumReferences() {
        return refs;
    }
    
    /**
     * Returns true if this resource is virtual.
     * <p>
     * A resource is virtual when it does not hold a concrete resource
     * and is not set as undefined.
     * 
     * @return 
     */
    public boolean isVirtual() {
        return resource == null && !undefined;
    }
    /**
     * Returns true if this resource is primitive.
     * <p>
     * A resource is primitive when it holds a concrete resource without a
     * corresponding render object. Primitive resources are handled niavely,
     * because they are not directly associated with a render object.
     * 
     * @return 
     */
    public boolean isPrimitive() {
        return resource != null && object == null;
    }
    /**
     * Returns true if this resource is referenced by users other than the
     * producer.
     * 
     * @return 
     */
    public boolean isReferenced() {
        return refs > 0;
    }
    /**
     * Returns true if this resource is used (including the producer).
     * 
     * @return 
     */
    public boolean isUsed() {
        return refs >= 0;
    }
    /**
     * Returns true if this resource is marked as undefined.
     * 
     * @return 
     */
    public boolean isUndefined() {
        return undefined;
    }
    /**
     * 
     * @return 
     */
    public boolean isTemporary() {
        return temporary;
    }
    /**
     * Return true if this resource is available for reading.
     * <p>
     * This is true typically after the first release occurs.
     * 
     * @return 
     */
    public boolean isReadAvailable() {
        return released.get();
    }
    
    @Override
    public String toString() {
        return "RenderResource["+producer+", "+ticket+"]";
    }
    
}
