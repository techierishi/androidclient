/*
 * Kontalk Android client
 * Copyright (C) 2014 Kontalk Devteam <devteam@kontalk.org>

 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.

 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.kontalk.message;

import java.io.File;

import org.kontalk.crypto.Coder;

import android.util.Log;



/**
 * A location message component.
 * @author Daniele Ricci
 * @author Andrea Cappelli
 */
public class LocationComponent extends MessageComponent <Location> {
    private File mCachedMap;
    public LocationComponent(double lat, double lon, File cachedMap) {
        super(new Location(lat,lon), 0, false, Coder.SECURITY_CLEARTEXT);
        mCachedMap = cachedMap;
    }

    public double getLatitude() {
        return mContent.getLatitude();
    }

    public double getLongitude() {
        return mContent.getLongitude();
    }

    public File getCachedMap () {
        Log.w ("PATH",""+mCachedMap.getAbsolutePath());
        return mCachedMap;
    }
}
