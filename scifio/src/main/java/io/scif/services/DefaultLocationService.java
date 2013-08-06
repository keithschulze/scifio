/*
 * #%L
 * SCIFIO library for reading and converting scientific file formats.
 * %%
 * Copyright (C) 2011 - 2013 Open Microscopy Environment:
 *   - Board of Regents of the University of Wisconsin-Madison
 *   - Glencoe Software, Inc.
 *   - University of Dundee
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * 
 * The views and conclusions contained in the software and documentation are
 * those of the authors and should not be interpreted as representing official
 * policies, either expressed or implied, of any organization.
 * #L%
 */

package io.scif.services;

import io.scif.io.IRandomAccess;
import io.scif.io.IStreamAccess;
import io.scif.io.NIOFileHandle;
import io.scif.io.NIOService;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.plugin.PluginInfo;
import org.scijava.plugin.PluginService;
import org.scijava.service.AbstractService;
import org.scijava.service.Service;

/**
 * Default {@link io.scif.services.LocationService} implementation
 * 
 * @see io.scif.services.LocationService
 * @author Mark Hiner
 */
@Plugin(type = Service.class)
public class DefaultLocationService extends AbstractService implements
	LocationService
{

	// -- Fields --

	@Parameter
	private LogService log;

	@Parameter
	private NIOService nioService;

	/** Map from given filenames to actual filenames. */
	private HashMap<String, Object> idMap = new HashMap<String, Object>();

	private volatile boolean cacheListings = false;

	// By default, cache for one hour.
	private volatile long cacheNanos = 60L * 60L * 1000L * 1000L * 1000L;

	protected class ListingsResult {

		public final String[] listing;
		public final long time;

		ListingsResult(final String[] listing, final long time) {
			this.listing = listing;
			this.time = time;
		}
	}

	private ConcurrentHashMap<String, ListingsResult> fileListings =
		new ConcurrentHashMap<String, ListingsResult>();

	// -- Location API methods --

	/*
	 * @see io.scif.services.LocationService#reset()
	 */
	public void reset() {
		cacheNanos = 60L * 60L * 1000L * 1000L * 1000L;
		fileListings.clear();
		getIdMap().clear();
	}

	/*
	 * @see io.scif.services.LocationService#cacheDirectoryListings(boolean)
	 */
	public void cacheDirectoryListings(final boolean cache) {
		cacheListings = cache;
	}

	/*
	 * @see io.scif.services.LocationService#setCacheDirectoryTimeout(double)
	 */
	public void setCacheDirectoryTimeout(final double sec) {
		cacheNanos = (long) (sec * 1000. * 1000. * 1000.);
	}

	/*
	 * @see io.scif.services.LocationService#clearDirectoryListingsCache()
	 */
	public void clearDirectoryListingsCache() {
		fileListings = new ConcurrentHashMap<String, ListingsResult>();
	}

	/*
	 * @see io.scif.services.LocationService#cleanStaleCacheEntries()
	 */
	public void cleanStaleCacheEntries() {
		final long t = System.nanoTime() - cacheNanos;
		final ArrayList<String> staleKeys = new ArrayList<String>();
		for (final String key : fileListings.keySet()) {
			if (fileListings.get(key).time < t) {
				staleKeys.add(key);
			}
		}
		for (final String key : staleKeys) {
			fileListings.remove(key);
		}
	}

	/*
	 * @see io.scif.services.LocationService#
	 * mapId(java.lang.String, java.lang.String)
	 */
	public void mapId(final String id, final String filename) {
		if (id == null) return;
		if (filename == null) getIdMap().remove(id);
		else getIdMap().put(id, filename);
		log.debug("Location.mapId: " + id + " -> " + filename);
	}

	/*
	 * @see io.scif.services.LocationService#
	 * mapFile(java.lang.String, io.scif.io.IRandomAccess)
	 */
	public void mapFile(final String id, final IRandomAccess ira) {
		if (id == null) return;
		if (ira == null) getIdMap().remove(id);
		else getIdMap().put(id, ira);
		log.debug("Location.mapFile: " + id + " -> " + ira);
	}

	/*
	 * @see io.scif.services.LocationService#getMappedId(java.lang.String)
	 */
	public String getMappedId(final String id) {
		if (getIdMap() == null) return id;
		String filename = null;
		if (id != null && (getIdMap().get(id) instanceof String)) {
			filename = (String) getIdMap().get(id);
		}
		return filename == null ? id : filename;
	}

	/*
	 * @see io.scif.services.LocationService#getMappedFile(java.lang.String)
	 */
	public IRandomAccess getMappedFile(final String id) {
		if (getIdMap() == null) return null;
		IRandomAccess ira = null;
		if (id != null && (getIdMap().get(id) instanceof IRandomAccess)) {
			ira = (IRandomAccess) getIdMap().get(id);
		}
		return ira;
	}

	/*
	 * @see io.scif.services.LocationService#getIdMap()
	 */
	public HashMap<String, Object> getIdMap() {
		return idMap;
	}

	/*
	 * @see io.scif.services.LocationService#setIdMap(java.util.HashMap)
	 */
	public void setIdMap(final HashMap<String, Object> map) {
		if (map == null) throw new IllegalArgumentException("map cannot be null");
		idMap = map;
	}

	/*
	 * @see io.scif.services.LocationService#getHandle(java.lang.String)
	 */
	public IRandomAccess getHandle(final String id) throws IOException {
		return getHandle(id, false);
	}

	/*
	 * @see io.scif.services.LocationService#getHandle(java.lang.String, boolean)
	 */
	public IRandomAccess getHandle(final String id, final boolean writable)
		throws IOException
	{
		return getHandle(id, writable, true);
	}

	/*
	 * @see io.scif.services.LocationService#
	 * getHandle(java.lang.String, boolean, boolean)
	 */
	public IRandomAccess getHandle(final String id, final boolean writable,
		final boolean allowArchiveHandles) throws IOException
	{
		log.trace("getHandle(id = " + id + ", writable = " + writable + ")");
		IRandomAccess handle = getMappedFile(id);
		if (handle == null) {
			log.trace("no handle was mapped for this ID");
			final String mapId = getMappedId(id);

			final List<PluginInfo<IStreamAccess>> streamInfos =
				getContext().getPluginIndex().getPlugins(IStreamAccess.class);

			if (allowArchiveHandles) {
				for (final PluginInfo<IStreamAccess> info : streamInfos) {
					handle =
						getContext().getService(PluginService.class).createInstance(info);
					if (((IStreamAccess) handle).isConstructable(id)) {
						((IStreamAccess) handle).setFile(id);
						break;
					}
					handle = null;
				}
			}

			if (handle == null) handle =
				new NIOFileHandle(nioService, mapId, writable ? "rw" : "r");

		}
		log.trace("Location.getHandle: " + id + " -> " + handle);
		return handle;
	}

	/*
	 * @see io.scif.services.LocationService#checkValidId(java.lang.String)
	 */
	public void checkValidId(final String id) throws IOException {
		if (getMappedFile(id) != null) {
			// NB: The id maps directly to an IRandomAccess handle, so is valid. Do
			// not destroy an existing mapped IRandomAccess handle by closing it.
			return;
		}
		// NB: Try to actually open a handle to make sure it is valid. Close it
		// afterward so we don't leave it dangling. The process of doing this will
		// throw IOException if something goes wrong.
		getHandle(id).close();
	}

	/*
	 * @see io.scif.services.LocationService#getCachedListing(java.lang.String)
	 */
	public String[] getCachedListing(final String key) {
		ListingsResult listingsResult = null;
		if (cacheListings) {
			cleanStaleCacheEntries();
			listingsResult = fileListings.get(key);
		}
		return listingsResult == null ? null : listingsResult.listing;
	}

	/*
	 * @see io.scif.services.LocationService#
	 * putCachedListing(java.lang.String, java.lang.String[])
	 */
	public void putCachedListing(final String key, final String[] listing) {
		if (cacheListings) {
			fileListings.put(key, new ListingsResult(listing, System.nanoTime()));
		}
	}
}