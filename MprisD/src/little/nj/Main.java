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

import org.freedesktop.dbus.exceptions.DBusException;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static little.nj.CommonComponents.*;

public class Main
{
    private static class BootstrapServerInfo
    {
        public String  service;
        public String  name;
        public Integer port;

        public Set < ServerPlayerWrapper.BootstrapPlayerInfo > players;

        public BootstrapServerInfo ( String service,
                                     String name,
                                     Integer port,
                                     ServerPlayerWrapper.BootstrapPlayerInfo... players)
        {
            this.service = service;
            this.name = name;
            this.port = port;
            this.players = new HashSet <> ( Arrays.asList ( players ) );
        }

        public BootstrapServerInfo ()
        {
            this ( DEFAULT_SERVICE_TYPE, DEFAULT_SERVER_NAME,
                   DEFAULT_PORT );
        }
    }

    /**
     * Parses arguments, starts the server
     *
     * <dl>
     *     <dt>-s</dt><dd>Service Type</dd>
     *     <dt>-n</dt><dd>Service Name</dd>
     *     <dt>-p</dt><dd>Port Number</dd>
     * </dl>
     *
     * In addition to the above, a user can supply a
     * number of tuples of the form:
     *
     * busname service|null
     *
     * Where <em>busname</em> is the name under org.mpris.MediaPlayer2
     * where the object is located; <em>service</em> is the name of the
     * media player's service, if applicable or null if not; and the
     * final argument specifies whether the player supports Playlists
     *
     * @param args
     * @throws java.io.IOException
     * @throws org.freedesktop.dbus.exceptions.DBusException
     */
    public static void main ( String[] args ) throws IOException, DBusException
    {
        BootstrapServerInfo info = processArgs ( args );

        Daemon proc = new Daemon (
                info.service,
                info.name,
                info.port,
                info.players.toArray ( new ServerPlayerWrapper.BootstrapPlayerInfo[ info.players.size () ] )
        );

        new Thread ( proc ).start ();
    }

    private static BootstrapServerInfo processArgs ( String[] args )
    {
        BootstrapServerInfo rv = new BootstrapServerInfo ();

        String arg;
        for ( int i = 0, end = args.length - 1; i < end; ++i )
        {
            try
            {
                arg = args[i + 1];

                switch ( args[i] )
                {
                    case "-p":
                        rv.port = Integer.parseInt ( arg, 10 );
                        break;
                    case "-n":
                        rv.name = arg;
                        break;
                    case "-s":
                        rv.service = arg;
                        break;
                    default:
                        // Should be a player specification
                        String[] spec = arg.split ( "," );
                        rv.players.add (
                                new ServerPlayerWrapper.BootstrapPlayerInfo (
                                        args [ i ],
                                        spec [ 0 ].equals ( "null" ) ? null : spec [ 0 ]
                                )
                        );
                        break;
                }
                ++i;
            }
            catch ( Exception ex )
            {
                // We'll ignore this, we're guaranteed a valid
                // bootstrap even if we get messed up args
                ex.printStackTrace ();
            }
        }

        return rv;
    }
}
