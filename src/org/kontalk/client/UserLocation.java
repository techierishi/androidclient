package org.kontalk.client;

import org.jivesoftware.smack.packet.PacketExtension;
import org.jivesoftware.smack.provider.PacketExtensionProvider;
import org.xmlpull.v1.XmlPullParser;


/**
 * XEP-0080: User Location
 * http://xmpp.org/extensions/xep-0080.html
 * @author Andrea Cappelli
 * @author Daniele Ricci
 */
public class UserLocation implements PacketExtension {
    public static final String NAMESPACE = "http://jabber.org/protocol/geoloc";
    public static final String ELEMENT_NAME = "geoloc";

    private double mLatitude;
    private double mLongitude;

    public UserLocation(double latitude, double longitude) {
        mLatitude = latitude;
        mLongitude = longitude;
    }

    public double getLatitude() {
        return mLatitude;
    }

    public double getLongitude() {
        return mLongitude;
    }

    @Override
    public String getElementName() {
        return ELEMENT_NAME;
    }

    @Override
    public String getNamespace() {
        return NAMESPACE;
    }

    @Override
    public String toXML() {
        return new StringBuilder("<")
            .append(ELEMENT_NAME)
            .append(" xmlns='")
            .append(NAMESPACE)
            .append("'><lat>")
            .append(mLatitude)
            .append("</lat><lon>")
            .append(mLongitude)
            .append("</lon></")
            .append(ELEMENT_NAME)
            .append('>')
            .toString();
    }

    public static final class Provider implements PacketExtensionProvider {

        @Override
        public PacketExtension parseExtension(XmlPullParser parser) throws Exception {
            double lat = 0, lon = 0;
            boolean lat_found = false, lon_found = false;
            boolean in_lat = false, in_lon = false, done = false;

            while (!done)
            {
                int eventType = parser.next();

                if (eventType == XmlPullParser.START_TAG)
                {
                    if ("lon".equals(parser.getName())) {
                        in_lon = true;
                    }
                    else if ("lat".equals(parser.getName())) {
                        in_lat = true;
                    }
                }
                else if (eventType == XmlPullParser.END_TAG)
                {
                    if ("lon".equals(parser.getName())) {
                        in_lon = false;
                    }
                    else if ("lat".equals(parser.getName())) {
                        in_lat = false;
                    }
                    else if (ELEMENT_NAME.equals(parser.getName())) {
                        done = true;
                    }
                }
                else if (eventType == XmlPullParser.TEXT) {
                    if (in_lon) {
                        try {
                            lon = Double.parseDouble(parser.getText());
                            lon_found = true;
                        }
                        catch (NumberFormatException e) {
                        }
                    }
                    else if (in_lat) {
                        try {
                            lat = Double.parseDouble(parser.getText());
                            lat_found = true;
                        }
                        catch (NumberFormatException e) {
                        }
                    }
                }
            }

            if (lon_found && lat_found)
                return new UserLocation(lat, lon);
            else
                return null;
        }

    }
}

