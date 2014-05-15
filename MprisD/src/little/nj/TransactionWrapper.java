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

import static little.nj.CommonComponents.ClientCommand;
import static little.nj.CommonComponents.ServerResponse;

class TransactionWrapper
{
    TransactionWrapper ( Daemon.Beastie rsvp, ClientCommand cause, ServerResponse effect )
    {
        this.rsvp = rsvp;
        this.cause = cause;
        this.effect = effect;

        effect.id = cause.id;
    }

    TransactionWrapper ( Daemon.Beastie rsvp, ClientCommand cause )
    {
        this ( rsvp, cause, new ServerResponse () );
    }

    final Daemon.Beastie rsvp;
    final ClientCommand cause;
    final ServerResponse effect;
}
