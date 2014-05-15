/*
 * Copyright (c) 2014. Nicholas Little < arealityfarbetween@googlemail.com >
 *
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */

package little.nj;

import org.freedesktop.DBus;
import org.freedesktop.dbus.DBusConnection;
import org.freedesktop.dbus.Variant;
import org.freedesktop.dbus.exceptions.DBusException;

import java.util.Map;

public class PropertiesHelper
{
    private final DBus.Properties m_prop;
    private final String m_iface;

    public PropertiesHelper ( DBusConnection conn, String busname, String object, String iface )
            throws DBusException
    {
        m_prop = conn.getRemoteObject ( busname, object, DBus.Properties.class );
        m_iface = iface;
    }

    public <T> T Get ( String name )
    {
        return m_prop.Get ( m_iface, name );
    }

    public Map < String, Variant > GetAll ( )
    {
        return m_prop.GetAll ( m_iface );
    }

    public <T> void Set ( String name, T value )
    {
        m_prop.Set ( m_iface, name, value );
    }
}
