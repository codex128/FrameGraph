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

import codex.renthyl.FrameGraph;
import codex.renthyl.modules.ModuleIndex;
import codex.renthyl.debug.GraphEventCapture;
import codex.renthyl.definitions.ResourceDef;
import com.jme3.texture.FrameBuffer;
import com.jme3.texture.Texture;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;

/**
 * Manages {@link ResourceView} declarations, references, and
 * releases for a single framegraph.
 * 
 * @author codex
 */
public class ResourceList {
    
    /**
     * Initial size of the resource ArrayList.
     */
    private static final int INITIAL_SIZE = 20;
    
    /**
     * Maximum time to wait in milliseconds before throwing an exception.
     */
    public static final long WAIT_TIMEOUT = 5000;
    
    private final FrameGraph frameGraph;
    private RenderObjectMap map;
    private GraphEventCapture cap;
    private ArrayList<ResourceView> resources = new ArrayList<>(INITIAL_SIZE);
    private final LinkedList<FutureReference> futureRefs = new LinkedList<>();
    private final HashMap<String, RenderObject> objectCache = new HashMap<>();
    private int nextSlot = 0;
    private int textureBinds = 0;
    
    /**
     * 
     * @param frameGraph 
     */
    public ResourceList(FrameGraph frameGraph) {
        this.frameGraph = frameGraph;
    }
    
    private <T> ResourceView<T> create(ResourceUser producer, ResourceDef<T> def, String name) {
        ResourceView res = new ResourceView<>(producer, def, new ResourceTicket<>(name));
        res.getTicket().setLocalIndex(add(res));
        return res;
    }
    private <T> ResourceView<T> locate(ResourceTicket<T> ticket) {
        return locate(ticket, true);
    }
    private <T> ResourceView<T> locate(ResourceTicket<T> ticket, boolean failOnMiss) {
        if (ticket == null) {
            if (failOnMiss) {
                throw new NullPointerException("Ticket cannot be null.");
            }
            return null;
        }
        final int i = ticket.getWorldIndex();
        if (i < 0) {
            if (failOnMiss) {
                throw new NullPointerException(ticket+" does not point to any resource (negative index).");
            }
            return null;
        }
        if (i < resources.size()) {
            ResourceView<T> res = resources.get(i);
            if (res != null) {
                return res;
            }
            if (failOnMiss) {
                throw new NullPointerException(ticket+" points to null resource.");
            }
        }
        if (failOnMiss) {
            throw new IndexOutOfBoundsException(ticket+" is out of bounds for size "+resources.size());
        }
        return null;
    }
    private ResourceView fastLocate(ResourceTicket ticket) {
        return resources.get(ticket.getWorldIndex());
    }
    private int add(ResourceView res) {
        assert res != null;
        if (nextSlot >= resources.size()) {
            // add resource to end of list
            resources.add(res);
            nextSlot++;
            return resources.size()-1;
        } else {
            // insert resource into available slot
            int i = nextSlot;
            resources.set(i, res);
            // find next available slot
            while (++nextSlot < resources.size()) {
                if (resources.get(nextSlot) == null) {
                    break;
                }
            }
            return i;
        }
    }
    private ResourceView remove(int index) {
        ResourceView prev = resources.set(index, null);
        if (prev != null && prev.isReferenced()) {
            throw new IllegalStateException("Cannot remove "+prev+" because it is referenced.");
        }
        nextSlot = Math.min(nextSlot, index);
        return prev;
    }
    
    /**
     * Returns true if the ticket can be used to locate a resource.
     * <p>
     * <em>Use {@link ResourceTicket#validate(com.jme3.renderer.framegraph.ResourceTicket)} instead.</em>
     * 
     * @param ticket
     * @return 
     */
    public boolean validate(ResourceTicket ticket) {
        return ResourceTicket.validate(ticket);
    }
    
    /**
     * Declares a new resource.
     * 
     * @param <T>
     * @param producer
     * @param def
     * @param store
     * @return 
     */
    public <T> ResourceTicket<T> declare(ResourceUser producer, ResourceDef<T> def, ResourceTicket<T> store) {
        String name = (store != null ? store.getName() : null);
        ResourceView<T> resource = create(producer, def, name);
        if (cap != null) cap.declareResource(resource.getIndex(), name);
        return resource.getTicket().copyIndexTo(store);
    }
    
    /**
     * Declares a temporary resource with an unregistered ticket.
     * <p>
     * Temporary resources do not participate in culling.
     * 
     * @param <T>
     * @param producer
     * @param def
     * @param store
     * @return 
     */
    public <T> ResourceTicket<T> declareTemporary(ResourceUser producer, ResourceDef<T> def, ResourceTicket<T> store) {
        store = declare(producer, def, store);
        locate(store).setTemporary(true);
        return store;
    }
    
    /**
     * If the ticket contains a valid object ID, that render object will be reserved
     * at the index.
     * <p>
     * Reserved objects cannot be allocated to another resource before the indexed
     * pass occurs, unless that object is also reserved by another resource.
     * 
     * @param passIndex
     * @param ticket 
     */
    public void reserve(ModuleIndex passIndex, ResourceTicket ticket) {
        if (ticket.getObjectId() >= 0) {
            map.reserve(ticket.getObjectId(), passIndex);
            ticket.copyObjectTo(locate(ticket).getTicket());
        }
    }
    
    /**
     * Makes reservations at the index for each {@link RenderObject} referenced by the tickets.
     * 
     * @param passIndex
     * @param tickets 
     * @see RenderObjectMap#reserve(long, com.jme3.renderer.framegraph.PassIndex)
     */
    public void reserve(ModuleIndex passIndex, ResourceTicket... tickets) {
        for (ResourceTicket t : tickets) {
            reserve(passIndex, t);
        }
    }
    
    private void reference(ModuleIndex index, String user, ResourceTicket ticket, boolean optional) {
        boolean sync = !frameGraph.isAsync();
        if (optional && sync && !ResourceTicket.validate(ticket)) {
            return;
        }
        ResourceView resource = locate(ticket, sync);
        if (resource != null) {
            resource.reference(index);
            if (cap != null) cap.referenceResource(resource.getIndex(), ticket.getName());
        } else {
            // save for later, since the resource hasn't been declared yet
            futureRefs.add(new FutureReference(index, ticket, optional, user));
        }
    }
    
    /**
     * References the resource associated with the ticket.
     * <p>
     * The pass index indicates when the resource will be acquired by the entity
     * which is referencing the resource, which is important for determining resource
     * lifetime.
     * 
     * @param passIndex render pass index
     * @param ticket 
     * @param user 
     */
    public void reference(ModuleIndex passIndex, String user, ResourceTicket ticket) {
        reference(passIndex, user, ticket, false);
    }
    
    /**
     * References the resource associated with the ticket if the ticket
     * is not null and does not have a negative world index.
     * 
     * @param passIndex render pass index
     * @param user
     * @param ticket
     */
    public void referenceOptional(ModuleIndex passIndex, String user, ResourceTicket ticket) {
        reference(passIndex, user, ticket, true);
    }
    
    /**
     * References resources associated with the tickets.
     * 
     * @param passIndex render pass index
     * @param user
     * @param tickets 
     */
    public void reference(ModuleIndex passIndex, String user, ResourceTicket... tickets) {
        for (ResourceTicket t : tickets) {
            reference(passIndex, user, t, false);
        }
    }
    
    /**
     * Optionally references resources associated with the tickets.
     * 
     * @param passIndex render pass index
     * @param user
     * @param tickets 
     */
    public void referenceOptional(ModuleIndex passIndex, String user, ResourceTicket... tickets) {
        for (ResourceTicket t : tickets) {
            reference(passIndex, user, t, true);
        }
    }
    
    /**
     * Gets the definition of the resource associated with the ticket.
     * 
     * @param <T>
     * @param <R>
     * @param type
     * @param ticket
     * @return 
     */
    public <T, R extends ResourceDef<T>> R getDefinition(Class<R> type, ResourceTicket<T> ticket) {
        ResourceDef<T> def = locate(ticket).getDefinition();
        if (type.isAssignableFrom(def.getClass())) {
            return (R)def;
        }
        return null;
    }
    
    /**
     * Marks the resource associated with the ticket as undefined.
     * <p>
     * Undefined resources cannot hold objects. If an undefined resource is acquired acquired (unless with
     * {@link #acquireOrElse(com.jme3.renderer.framegraph.ResourceTicket, java.lang.Object) acquireOrElse}),
     * an exception will occur.
     * 
     * @param ticket 
     */
    public void setUndefined(ResourceTicket ticket) {
        ResourceView resource = locate(ticket);
        resource.setUndefined();
        if (cap != null) cap.setResourceUndefined(resource.getIndex(), ticket.getName());
    }
    
    public void setUndefined(ResourceTicket... tickets) {
        for (ResourceTicket t : tickets) {
            setUndefined(t);
        }
    }
    
    /**
     * Marks the existing object held be the resource associated with the ticket as constant.
     * <p>
     * Constant objects cannot be reallocated until the end of the frame.
     * 
     * @param ticket 
     */
    public void setConstant(ResourceTicket ticket) {
        RenderObject obj = locate(ticket).getObject();
        if (obj != null) {
            obj.setConstant(true);
            if (cap != null) cap.setObjectConstant(obj.getId());
        }
    }
    
    /**
     * Marks the resource associated with the ticket if the ticket is not
     * null and does not have a negative world index.
     * 
     * @param ticket 
     */
    public void setConstantOptional(ResourceTicket ticket) {
        if (validate(ticket)) {
            setConstant(ticket);
        }
    }
    
    /**
     * Returns true if the resource associated with the ticket is virtual.
     * <p>
     * A resource is virtual if it does not contain a concrete object and is
     * not marked as undefined.
     * 
     * @param ticket
     * @param optional
     * @return 
     */
    public boolean isVirtual(ResourceTicket ticket, boolean optional) {
        if (!optional || validate(ticket)) {
            return locate(ticket).isVirtual();
        }
        return true;
    }
    
    /**
     * Forces the current thread to wait until the resource at the ticket is
     * available for reading or a timeout occurs.
     * <p>
     * A resource becomes available for reading after being released by the declaring pass.
     * Then all waiting passes may access it for reading only.
     * <p>
     * The operation is skipped if the ticket is invalid.
     * 
     * @param ticket ticket to locate resource with
     * @param thread current thread
     */
    public void waitForResource(ResourceTicket ticket, int thread) {
        if (ResourceTicket.validate(ticket)) {
            // wait for resource to become available to this context
            long start = System.currentTimeMillis();
            ResourceView res;
            // TODO: determine why not locating the resource on each try results in timeouts.
            while (!(res = fastLocate(ticket)).isReadAvailable()) {
                if (System.currentTimeMillis()-start >= WAIT_TIMEOUT) {
                    throw new IllegalStateException("Thread "+thread+": Resource at "+ticket+" was assumed "
                            + "unreachable after "+WAIT_TIMEOUT+" milliseconds.");
                }
            }
            // claim read permisions
            // for resources that are read concurrent, this won't matter
            if (!res.claimReadPermissions()) {
                waitForResource(ticket, thread);
            }
        }
    }
    
    /**
     * Returns true if the resource at the ticket is asynchronous.
     * 
     * @param ticket
     * @return 
     */
    public boolean isAsync(ResourceTicket ticket) {
        if (ResourceTicket.validate(ticket)) {
            return locate(ticket).getLifeTime().isAsync();
        }
        return false;
    }
    
    /**
     * Acquires the object held by the given resource.
     * <p>
     * If the object does have an object associated with it (virtual), one will either
     * be created or reallocated by the {@link RenderObjectMap}.
     * <p>
     * The object's id is written to the ticket.
     * 
     * @param <T>
     * @param resource
     * @param ticket
     * @return 
     */
    protected <T> T acquire(ResourceView<T> resource, ResourceTicket<T> ticket) {
        if (!resource.isUsed()) {
            throw new IllegalStateException(resource+" was unexpectedly acquired.");
        }
        if (resource.isVirtual()) {
            map.allocate(resource, frameGraph.isAsync());
        }
        if (cap != null) cap.acquireResource(resource.getIndex(), ticket.getName());
        resource.getTicket().copyObjectTo(ticket);
        return resource.getResource();
    }
    
    /**
     * Acquires and returns the value associated with the resource at the ticket.
     * <p>
     * If the resource is virtual (not holding a object), then either an existing
     * object will be reallocated to the resource or a new object will be created.
     * 
     * @param <T>
     * @param ticket
     * @return 
     */
    public <T> T acquire(ResourceTicket<T> ticket) {
        ResourceView<T> resource = locate(ticket);
        if (resource.isUndefined()) {
            throw new NullPointerException("Cannot acquire undefined resource.");
        }
        return acquire(resource, ticket);
    }
    
    /**
     * If the ticket is not null and has a positive or zero world index, an object
     * will be acquired for the resource and returned.
     * <p>
     * Otherwise, the given default value will be returned.
     * 
     * @param <T>
     * @param ticket
     * @param value default value (may be null)
     * @return 
     */
    public <T> T acquireOrElse(ResourceTicket<T> ticket, T value) {
        if (validate(ticket)) {
            ResourceView<T> resource = locate(ticket);
            if (!resource.isUndefined()) {
                return acquire(resource, ticket);
            }
        }
        return value;
    }
    
    /**
     * Acquires and assigns textures as color targets to the framebuffer.
     * <p>
     * If a texture is already assigned to the framebuffer at the same color target index,
     * then nothing will be changed at that index.
     * <p>
     * Existing texture targets beyond the number of tickets passed will be removed.
     * 
     * @param fbo
     * @param tickets 
     */
    public void acquireColorTargets(FrameBuffer fbo, ResourceTicket<? extends Texture>... tickets) {
        acquireColorTargets(fbo, null, tickets);
    }
    
    /**
     * Acquires and assigns textures as color targets to the framebuffer.
     * 
     * @param fbo
     * @param texArray array to populate with acquired textures, or null
     * @param tickets
     * @return populated texture array
     */
    public Texture[] acquireColorTargets(FrameBuffer fbo, Texture[] texArray, ResourceTicket<? extends Texture>[] tickets) {
        if (tickets.length == 0) {
            fbo.clearColorTargets();
            fbo.setUpdateNeeded();
            return texArray;
        }
        while (tickets.length < fbo.getNumColorTargets()) {
            fbo.removeColorTarget(fbo.getNumColorTargets()-1);
            fbo.setUpdateNeeded();
        }
        int i = 0;
        for (int n = Math.min(fbo.getNumColorTargets(), tickets.length); i < n; i++) {
            Texture t = replaceColorTarget(fbo, tickets[i], i);
            if (texArray != null) {
                texArray[i] = t;
            }
        }
        for (; i < tickets.length; i++) {
            Texture t = acquire(tickets[i]);
            if (texArray != null) {
                texArray[i] = t;
            }
            fbo.addColorTarget(FrameBuffer.target(t));
            fbo.setUpdateNeeded();
            if (cap != null) cap.bindTexture(tickets[i].getWorldIndex(), tickets[i].getName());
            textureBinds++;
        }
        return texArray;
    }
    
    /**
     * Acquires the texture associated with the ticket and assigns it to the framebuffer.
     * 
     * @param <T>
     * @param fbo
     * @param ticket
     * @return acquired texture
     */
    public <T extends Texture> T acquireColorTarget(FrameBuffer fbo, ResourceTicket<T> ticket) {
        if (ticket == null) {
            if (fbo.getNumColorTargets() > 0) {
                fbo.clearColorTargets();
                fbo.setUpdateNeeded();
            }
            return null;
        }
        while (fbo.getNumColorTargets() > 1) {
            fbo.removeColorTarget(fbo.getNumColorTargets()-1);
            fbo.setUpdateNeeded();
        }
        return replaceColorTarget(fbo, ticket, 0);
    }
    
    private <T extends Texture> T replaceColorTarget(FrameBuffer fbo, ResourceTicket<T> ticket, int i) {
        if (i < fbo.getNumColorTargets()) {
            Texture existing = fbo.getColorTarget(i).getTexture();
            T acquired = acquire(ticket);
            if (acquired != existing) {
                fbo.setColorTarget(i, FrameBuffer.target(acquired));
                fbo.setUpdateNeeded();
                if (cap != null) cap.bindTexture(ticket.getWorldIndex(), ticket.getName());
                textureBinds++;
            }
            return acquired;
        } else {
            T acquired = acquire(ticket);
            fbo.addColorTarget(FrameBuffer.target(acquired));
            fbo.setUpdateNeeded();
            return acquired;
        }
    }
    
    /**
     * Acquires and assigns a texture as the depth target to the framebuffer.
     * <p>
     * If the texture is already assigned to the framebuffer as the depth target,
     * the nothing changes.
     * 
     * @param <T>
     * @param fbo
     * @param ticket 
     * @return  
     */
    public <T extends Texture> T acquireDepthTarget(FrameBuffer fbo, ResourceTicket<T> ticket) {
        T acquired = acquire(ticket);
        FrameBuffer.RenderBuffer target = fbo.getDepthTarget();
        if (target == null || acquired != target.getTexture()) {
            fbo.setDepthTarget(FrameBuffer.target(acquired));
            fbo.setUpdateNeeded();
            if (cap != null) cap.bindTexture(ticket.getWorldIndex(), ticket.getName());
            textureBinds++;
        }
        return acquired;
    }
    
    /**
     * Acquires cached resource at the key.
     * <p>
     * If no cached resource exists at the key, the resource will be set to
     * undefined. If the resource is not virtual, an exception will be thrown.
     * <p>
     * This operation is not threadsafe if two threads request the same resource
     * at once.
     * 
     * @param <T>
     * @param ticket
     * @param key
     * @return 
     */
    public <T> T acquireCached(ResourceTicket ticket, String key) {
        ResourceView<T> res = locate(ticket);
        if (!res.isVirtual()) {
            throw new IllegalStateException(res+" must be virtual to acquire cached resource.");
        }
        if (map.allocateFromCache(objectCache, res, key)) {
            return res.getResource();
        }
        res.setUndefined();
        return null;
    }
    
    /**
     * Directly assign the resource associated with the ticket to the value.
     * <p>
     * A render object is not created to store the value. Instead, the render resource
     * will directly store the value. Note that the value will be lost when the
     * render resource is destroyed.
     * <p>
     * This is intended to called in place of {@link #acquire(com.jme3.renderer.framegraph.ResourceTicket)}.
     * 
     * @param <T>
     * @param ticket 
     * @param value 
     */
    public <T> void setPrimitive(ResourceTicket<T> ticket, T value) {
        locate(ticket).setPrimitive(value);
    }
    
    /**
     * Releases the resource from use.
     * 
     * @param ticket 
     */
    public void release(ResourceTicket ticket) {
        ResourceView resource = locate(ticket);
        if (cap != null) cap.releaseResource(resource.getIndex(), ticket.getName());
        if (!resource.release()) {
            if (cap != null && resource.getObject() != null) {
                cap.releaseObject(resource.getObject().getId());
            }
            remove(ticket.getWorldIndex());
            if (!resource.isVirtual() && !resource.isUndefined()) {
                ResourceDef def = resource.getDefinition();
                if (def != null && def.isDisposeOnRelease()) {
                    if (!resource.isPrimitive()) {
                        map.dispose(resource);
                    } else {
                        resource.getDefinition().dispose(resource.getResource());
                    }
                }
                resource.setObject(null);
            }
        }
    }
    
    /**
     * Releases the ticket if the ticket is not null and contains a non-negative
     * world index.
     * 
     * @param ticket
     * @return 
     */
    public boolean releaseOptional(ResourceTicket ticket) {
        if (ResourceTicket.validate(ticket)) {
            release(ticket);
            return true;
        }
        return false;
    }
    
    /**
     * Releases the resources obtained by the tickets from use.
     * 
     * @param tickets 
     */
    public void release(ResourceTicket... tickets) {
        for (ResourceTicket t : tickets) {
            release(t);
        }
    }
    
    /**
     * Optionally releases the resources obtained by the tickets from use.
     * 
     * @param tickets 
     */
    public void releaseOptional(ResourceTicket... tickets) {
        for (ResourceTicket t : tickets) {
            releaseOptional(t);
        }
    }
    
    /**
     * Caches the object currently associated with the ticket's resource view.
     * <p>
     * The resource view cannot be virtual or primitive, otherwise can exception
     * is thrown.
     * 
     * @param ticket 
     * @param key 
     */
    public void cache(ResourceTicket ticket, String key) {
        ResourceView res = locate(ticket);
        if (res.isVirtual()) {
            throw new IllegalStateException("Cannot cache because resource is virtual.");
        }
        if (res.isPrimitive()) {
            throw new IllegalStateException("Cannot cache primitive resource.");
        }
        map.cache(objectCache, res.getObject().getId(), key);
    }
    
    /**
     * Prepares this for rendering.
     * <p>
     * This should only be called once per frame.
     * @param map
     * @param cap
     */
    public void beginRenderFrame(RenderObjectMap map, GraphEventCapture cap) {
        this.map = map;
        this.cap = cap;
        textureBinds = 0;
    }
    
    /**
     * Cleans up after rendering.
     */
    public void endRenderFrame() {
        map.flushCache(objectCache);
    }
    
    /**
     * Applies all missed references.
     */
    public void applyFutureReferences() {
        for (FutureReference ref : futureRefs) {
            if (!ref.optional || ResourceTicket.validate(ref.ticket)) {
                if (!ResourceTicket.validate(ref.ticket)) {
                    throw new NullPointerException(ref.ticket+" from "+ref.user+" is invalid.");
                }
                locate(ref.ticket).reference(ref.index);
            }
        }
        futureRefs.clear();
    }
    
    /**
     * Culls all resources and resource producers found to be unused.
     * <p>
     * This should only be called after resource users have fully counted their
     * references, and prior to execution.
     */
    public void cullUnreferenced() {
        LinkedList<ResourceView> cull = new LinkedList<>();
        for (ResourceView r : resources) {
            if (r != null && !r.isReferenced() && !r.isTemporary()) {
                cull.add(r);
            }
        }
        ResourceView resource;
        while ((resource = cull.pollFirst()) != null) {
            // dereference producer of resource
            ResourceUser producer = resource.getProducer();
            if (producer == null) {
                remove(resource.getIndex());
                continue;
            }
            producer.dereference();
            if (!producer.isUsed()) {
                for (ResourceTicket t : producer.getInputTickets()) {
                    if (!validate(t)) {
                        continue;
                    }
                    ResourceView r = locate(t);
                    r.release();
                    if (!r.isReferenced()) {
                        cull.addLast(r);
                    }
                }
                for (ResourceTicket t : producer.getOutputTickets()) {
                    if (!t.hasSource()) {
                        remove(t.getLocalIndex());
                    }
                }
            }
        }
    }
    
    /**
     * Clears the resource list.
     */
    public void clear() {
        // TODO: throw exceptions for unreleased resources.
        int size = resources.size();
        resources = new ArrayList<>(size);
        nextSlot = 0;
        if (cap != null) {
            cap.clearResources(size);
            cap.value("framebufferTextureBinds", textureBinds);
        }
    }
    
    /**
     * Gets the number of known texture binds that occured during
     * the last render frame.
     * 
     * @return 
     */
    public int getNumTextureBinds() {
        return textureBinds;
    }
    
    /**
     * Returns the size of the object cache.
     * 
     * @return 
     */
    public int getObjectCacheSize() {
        return objectCache.size();
    }
    
    /**
     * Represents a reference to a resource that will exist in the future.
     */
    private static class FutureReference {
        
        public final ModuleIndex index;
        public final ResourceTicket ticket;
        public final boolean optional;
        public final String user;

        public FutureReference(ModuleIndex index, ResourceTicket ticket, boolean optional, String user) {
            this.index = index;
            this.ticket = ticket;
            this.optional = optional;
            this.user = user;
        }
        
    }
    
}
