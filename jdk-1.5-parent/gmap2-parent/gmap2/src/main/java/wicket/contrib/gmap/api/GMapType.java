/*
 * 
 * ==============================================================================
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package wicket.contrib.gmap.api;

import java.io.Serializable;

import wicket.contrib.gmap.GMap2;

/**
 * Represents an Google Maps API's
 * <a href="http://www.google.com/apis/maps/documentation/reference.html#GMapType">GMapType</a>.
 */
public enum GMapType implements Serializable {
    G_NORMAL_MAP, G_SATELLITE_MAP, G_HYBRID_MAP, G_AERIAL_MAP, G_AERIAL_HYBRID_MAP,
    G_PHYSICAL_MAP, G_MAPMAKER_NORMAL_MAP, G_MAPMAKER_HYBRID_MAP, 
    G_MOON_ELEVATION_MAP, G_MOON_VISIBLE_MAP, G_MARS_ELEVATION_MAP, 
    G_MARS_VISIBLE_MAP, G_MARS_INFRARED_MAP, G_SKY_VISIBLE_MAP,
    G_SATELLITE_3D_MAP;

    public static final GMapType[] G_DEFAULT_MAP_TYPES = 
        { GMapType.G_NORMAL_MAP, GMapType.G_HYBRID_MAP, GMapType.G_SATELLITE_MAP };
    
    public String getJSsetMapType(GMap2 map)
    {
        return map.getJSinvoke("setMapType(" + name() + ")");
    }

    public String getJSaddMapType(GMap2 map) {
        return map.getJSinvoke("addMapType(" + name() + ")");
    }

    public String getJSremoveMapType(GMap2 map) {
        return map.getJSinvoke("removeMapType(" + name() + ")");
    }   
}