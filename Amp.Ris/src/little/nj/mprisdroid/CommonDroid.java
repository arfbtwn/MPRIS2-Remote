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

package little.nj.mprisdroid;

import android.app.Activity;
import little.nj.CommonComponents.ServerResponse;

import java.util.EventListener;

import static little.nj.CommonComponents.ClientCommand;

public class CommonDroid
{
    /**
     * Logging Tag
     */
    public static final String TAG = "Amp.Ris";

    /**
     * Essential fragments
     */
    public static final String FRAG_DATA = "Client";
    public static final String FRAG_LIST = "PlayerList";

    /*
     * Result codes
     */
    public static final int CONNECTION_LOST = Activity.RESULT_CANCELED;
    public static final int CONNECTION_FAILED = Activity.RESULT_OK;

    /**
     * Components issue commands through them
     */
    public interface OnClientCommandListener extends EventListener
    {
        void onClientCommand ( ClientCommand clientCommand );
        void onClientCommand ( ClientCommand clientCommand, OnServerResponseListener callback );
    }

    /**
     * Components receive responses through them
     */
    public interface OnServerResponseListener extends EventListener
    {
        void onServerResponse ( ServerResponse serverResponse );
    }

}
