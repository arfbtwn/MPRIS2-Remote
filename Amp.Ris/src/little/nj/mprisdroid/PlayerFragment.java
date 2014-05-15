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

import android.app.Fragment;
import android.content.Context;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Log;
import android.view.*;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.*;
import little.nj.mprisdroid.CommonDroid.OnClientCommandListener;

import java.io.File;
import java.lang.ref.WeakReference;
import java.net.URI;
import java.util.EventListener;
import java.util.Timer;
import java.util.TimerTask;

import static little.nj.CommonComponents.*;
import static little.nj.mprisdroid.CommonDroid.TAG;

public class PlayerFragment extends Fragment
{
    public static interface OnArtRequestListener extends EventListener
    {
        void onArtRequest ( Metadata data, ArtCacheHelper.OnArtReceivedListener callback );
    }

    /**
     * We'll retrieve the PlayerInfo when we are restored
     */
    public final static String INFO = "PlayerInfo";

    /**
     * This listener probably has an activity connected to it
     * somewhere in the chain
     */
    private WeakReference < OnClientCommandListener > m_listener;

    /**
     * Our data bean
     */
    private PlayerInfo m_info;

    /**
     * Our art helper
     */
    private WeakReference < OnArtRequestListener > m_art_listener;

    /**
     * Whether the user is currently seeking
     */
    private boolean seeking;

    /**
     * The last url that we loaded into the album art ImageView
     */
    private String last_cover;

    /**
     * The timestamp of the last received PlayerInfo
     */
    private long last_info;

    /**
     * The url of the last metadata we loaded into the view. This
     * property is here so we can throttle view updates from talkative
     * players
     */
    private String last_url;

    /**
     * A timer, to ensure that the seek bar is updated
     */
    private Timer timer;

    protected void signalClientCommand ( ClientCommand command )
    {
        OnClientCommandListener listener = m_listener.get ();

        if ( null == listener )
            return;

        listener.onClientCommand ( command );
    }

    protected void signalClientCommand ( ClientCommand command, CommonDroid.OnServerResponseListener callback )
    {
        OnClientCommandListener listener = m_listener.get ();

        if ( null == listener )
            return;

        listener.onClientCommand ( command, callback );
    }

    @Override
    public void onCreate ( Bundle savedInstanceState )
    {
        Log.d ( TAG, "PlayerFragment: onCreate" );
        super.onCreate ( savedInstanceState );

        setHasOptionsMenu ( true );

        onRestoreInstanceState ( savedInstanceState );
    }

    @Override
    public void onCreateOptionsMenu ( Menu menu, MenuInflater inflater )
    {
        Log.d ( TAG, "PlayerFragment: onCreateOptionsMenu" );
        inflater.inflate ( R.menu.player_bar, menu );
        super.onCreateOptionsMenu ( menu, inflater );
    }

    @Override
    public void onPrepareOptionsMenu ( Menu menu )
    {
        Log.d ( TAG, "PlayerFragment: onPrepareOptionsMenu" );
        super.onPrepareOptionsMenu ( menu );

        menu.findItem ( R.id.openuri ).setVisible ( PlayerState.Inactive != m_info.state );
    }

    @Override
    public View onCreateView ( LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState )
    {
        Log.d ( TAG, "PlayerFragment: onCreateView" );
        onRestoreInstanceState ( savedInstanceState );

        View v = inflater.inflate ( R.layout.player, container, false );

        initialise ( v );

        return v;
    }

    @Override
    public void onActivityCreated ( Bundle savedInstanceState )
    {
        Log.d ( TAG, "PlayerFragment: onActivityCreated" );
        super.onActivityCreated ( savedInstanceState );

        onRestoreInstanceState ( savedInstanceState );

        m_art_listener = new WeakReference <> ( (OnArtRequestListener) getActivity () );
        m_listener = new WeakReference <> ( (OnClientCommandListener) getActivity () );
    }

    @Override
    public void onStart ()
    {
        Log.d ( TAG, "PlayerFragment: onStart" );
        super.onStart ();

        timer = new Timer ();
        timer.schedule ( new SeekUpdater (), 0, 1000 );

        handleOrientation ( MotionEvent.ACTION_UP );
    }

    @Override
    public void onResume ()
    {
        Log.d ( TAG, "PlayerFragment: onResume" );
        super.onResume ();
        refresh ( getView () );
        refreshArt ();
    }

    @Override
    public void onStop ()
    {
        Log.d ( TAG, "PlayerFragment: onStop" );
        timer.cancel ();
        timer = null;
        last_cover = null;
        last_url = null;

        super.onStop ();
    }

    @Override
    public void onSaveInstanceState ( Bundle outState )
    {
        Log.d ( TAG, "PlayerFragment: onSaveInstanceState" );

        outState.putSerializable ( INFO, m_info );

        super.onSaveInstanceState ( outState );
    }

    protected void onRestoreInstanceState ( Bundle savedInstanceState )
    {
        Log.d ( TAG, "PlayerFragment: onRestoreInstanceState" );

        if ( null == savedInstanceState )
            return;

        Log.d ( TAG, "PlayerFragment: Restoring State" );

        m_info = (PlayerInfo) savedInstanceState.get ( INFO );
    }

    /**
     * Initialises the view upon creation, connects handlers to both
     * the active and inactive layouts
     *
     * @param v
     */
    private void initialise ( final View v )
    {
        getActivity ().setTitle ( m_info.name );

        v.setOnTouchListener ( new View.OnTouchListener () {
               @Override
               public boolean onTouch ( View view, MotionEvent motionEvent )
               {
                   handleOrientation ( motionEvent.getAction () );
                   return true;
               }
           } );

        initialiseDefault ( v );
        initialiseInactive ( v );
    }

    @Override
    public boolean onOptionsItemSelected ( MenuItem item )
    {
        switch ( item.getItemId () )
        {
            case R.id.openuri:
                BrowseFragment bf = BrowseFragment.createBrowseFragment ( m_info.id, null );
                getActivity ().getFragmentManager ().beginTransaction ()
                              .replace ( android.R.id.tabcontent, bf )
                              .addToBackStack ( BrowseFragment.BROWSE_ROOT )
                              .commit ();

                return true;
            case android.R.id.home:
                getFragmentManager ().popBackStack ();
                return true;
            default:
                return super.onOptionsItemSelected ( item );
        }
    }

    private void initialiseDefault ( final View v )
    {
        int [] on_click = new int [] {
                R.id.play, R.id.frev, R.id.ffwd, R.id.shuffle, R.id.loop,
                R.id.mute, R.id.playlists, R.id.fullscreen, R.id.raise,
                R.id.launch
        };

        for ( int i : on_click )
        {
            View bt1 = v.findViewById ( i );
            bt1.setOnClickListener ( m_click_listen );
        }

        SeekBar sb1 = (SeekBar)v.findViewById ( R.id.volume );
        sb1.setOnSeekBarChangeListener ( m_seek_listen );

        sb1 = (SeekBar)v.findViewById ( R.id.seek );
        sb1.setOnSeekBarChangeListener ( m_seek_listen );

        v.findViewById ( R.id.line1 ).setSelected ( true );
        v.findViewById ( R.id.line2 ).setSelected ( true );
        v.findViewById ( R.id.line3 ).setSelected ( true );
    }

    private void initialiseInactive ( final View v )
    {
        ImageView bt = (ImageView)v.findViewById ( R.id.launch );
        bt.setOnClickListener ( m_click_listen );
    }

    /**
     * When re-initialising your activity, be sure to call this method
     * after retrieving any system re-created PlayerFragments that you
     * may have around
     *
     * @param state
     */
    public void init ( PlayerInfo state )
    {
        m_info = state;
        last_info = System.currentTimeMillis ();
        last_url = null;
    }

    /**
     * Informs the fragment of a player information update
     *
     * @param update
     */
    public void sync ( PlayerInfo update )
    {
        m_info = update;
        last_info = System.currentTimeMillis ();

        refresh ( getView () );
        refreshArt ();
    }

    private void refresh ( final View v )
    {
        if ( null == m_info || null == v )
            return;

        getActivity ().runOnUiThread (
                new Runnable ()
                {
                    @Override
                    public void run ()
                    {
                        switch ( m_info.state )
                        {
                            case Inactive:
                                refreshInactiveData ( v );
                                v.findViewById ( R.id.inactive_layout ).setVisibility ( View.VISIBLE );
                                v.findViewById ( R.id.active_layout ).setVisibility ( View.GONE );
                                break;
                            default:
                                refreshDefaultData ( v );
                                v.findViewById ( R.id.inactive_layout ).setVisibility ( View.GONE );
                                v.findViewById ( R.id.active_layout ).setVisibility ( View.VISIBLE );
                                break;
                        }

                        getActivity ().invalidateOptionsMenu ();
                    }
                }
        );
    }

    private void refreshDefaultData ( final View v )
    {
        refreshControls ( v );

        refreshMetadata ( v );
    }

    private void refreshControls ( final View v )
    {
        if ( null == v )
            return;

        PlayerCapability cap = m_info.capability;

        v.findViewById ( R.id.play )
         .setEnabled ( cap.can_play || cap.can_pause );

        v.findViewById ( R.id.ffwd )
         .setEnabled ( cap.can_next );

        v.findViewById ( R.id.frev )
         .setEnabled ( cap.can_prev );

        v.findViewById ( R.id.seek )
         .setEnabled ( cap.can_seek );

        v.findViewById ( R.id.raise )
         .setEnabled ( cap.can_raise );

        v.findViewById ( R.id.fullscreen )
         .setEnabled ( cap.can_fullscreen );

        v.findViewById ( R.id.playlists )
         .setEnabled ( m_info.playlists > 0 );

        // Play/Pause
        ImageButton pl = (ImageButton)v.findViewById ( R.id.play );
        switch ( m_info.state )
        {
            case Playing:
                pl.setImageResource ( android.R.drawable.ic_media_pause );
                break;
            case Paused:
            case Stopped:
                pl.setImageResource ( android.R.drawable.ic_media_play );
                break;
        }

        // Volume update
        SeekBar sb1 = (SeekBar)v.findViewById ( R.id.volume );
        sb1.setProgress ( (int) (m_info.volume * 100) );

        // Muted
        ImageButton ib = (ImageButton)v.findViewById ( R.id.mute );
        if ( m_info.volume <= 0.0 )
        {
            ib.setImageResource ( R.drawable.ic_action_volume_muted );
        }
        else
        {
            ib.setImageResource ( R.drawable.ic_action_volume_on );
        }

        // Handle progress bar update
        if ( cap.can_seek && !seeking )
        {
            sb1 = (SeekBar) v.findViewById ( R.id.seek );

            if ( m_info.metadata.length > 0 )
            {
                sb1.setMax ( (int) ( m_info.metadata.length / 1000 ) );
                sb1.setProgress ( calculateSeek () );
            }
        }

        ib = (ImageButton)v.findViewById ( R.id.shuffle );
        ib.setImageResource ( m_info.shuffle ? R.drawable.ic_action_shuffle : R.drawable.ic_action_forward );

        ib = (ImageButton)v.findViewById ( R.id.loop_img );
        v.findViewById ( R.id.loop1 ).setVisibility ( View.INVISIBLE );
        switch ( m_info.loop )
        {
            case None:
                ib.setImageResource ( R.drawable.ic_action_download );
                break;
            case Track:
                v.findViewById ( R.id.loop1 ).setVisibility ( View.VISIBLE );
            case Playlist:
                ib.setImageResource ( R.drawable.ic_action_replay );
                break;
        }

        ib = (ImageButton)v.findViewById ( R.id.fullscreen );
        ib.setImageResource ( m_info.fullscreen
                        ? R.drawable.ic_action_return_from_full_screen
                        : R.drawable.ic_action_full_screen
        );
    }

    private int calculateSeek ()
    {
         /*
          * TODO: Handle the rate information presented by some players in the server
          */
        return PlayerState.Playing == m_info.state
                ? (int) ( ( System.currentTimeMillis () - last_info ) + ( m_info.position / 1000 ) )
                : (int) ( m_info.position / 1000 );
    }

    private void refreshMetadata ( final View v )
    {
        if ( null != last_url && last_url.equals ( m_info.metadata.url ) )
            return;

        String artist  = m_info.metadata.getFirstArtist (),
               title   = m_info.metadata.title,
               album   = m_info.metadata.album,
               uri     = m_info.metadata.url,
               trackId = m_info.metadata.trackId;

        last_url = uri;

        if ( !EMPTY.equals ( artist ) )
        {
            TextView tv1 = (TextView) v.findViewById ( R.id.line1 );
            tv1.setText ( artist );
            tv1 = (TextView) v.findViewById ( R.id.line2 );
            tv1.setText ( title );

            tv1 = (TextView) v.findViewById ( R.id.line3 );
            tv1.setText ( album );

            int vis = EMPTY.equals ( album ) ? View.GONE : View.VISIBLE;
            tv1.setVisibility ( vis );
            v.findViewById ( R.id.from ).setVisibility ( vis );
        }
        else
        {
            File furi = translateUri ( uri ),
                 ftid = translateUri ( trackId );

            TextView tv1 = (TextView) v.findViewById ( R.id.line1 );
            tv1.setText ( furi == null ? uri : furi.getName () );
            tv1 = (TextView) v.findViewById ( R.id.line2 );
            tv1.setText ( ftid == null ? trackId : ftid.getName () );

            v.findViewById ( R.id.line3 ).setVisibility ( View.GONE );
            v.findViewById ( R.id.from ).setVisibility ( View.GONE );
        }
    }

    private File translateUri ( String uri )
    {
        try
        {
            URI uuri = new URI ( uri );

            return new File ( uuri );
        }
        catch ( Exception e )
        {
            return null;
        }
    }

    private void refreshArt ()
    {
        OnArtRequestListener listener = m_art_listener.get ();

        if ( null == listener )
            return;

        listener.onArtRequest ( m_info.metadata, m_art_receiver );
    }

    private ArtCacheHelper.OnArtReceivedListener m_art_receiver =
            new ArtCacheHelper.OnArtReceivedListener () {
                @Override
                public void onArtReceived ( boolean valid, String uri, final File file )
                {
                    artFadeReplace ( file );
                }
            };

    /**
     * This function checks for landscape orientation and applies
     * animation appropriately to the overlayed player controls
     *
     * @param code
     */
    private void handleOrientation (int code)
    {
        Display display = ((WindowManager) getActivity ()
                .getSystemService ( Context.WINDOW_SERVICE ))
                .getDefaultDisplay();

        int orientation = display.getRotation();

        if (orientation == Surface.ROTATION_90 || orientation == Surface.ROTATION_270) {

            final View controls = getView ().findViewById ( R.id.controls );

            switch ( code )
            {
                case MotionEvent.ACTION_DOWN:
                    controls.setVisibility ( View.VISIBLE );
                    break;
                case MotionEvent.ACTION_UP:
                    setDisappearAnimation ( getActivity (), controls, 3000 );
                    break;
            }
        }
    }

    private void setAppearAnimation ( Context context, final View view, int offset )
    {
        Animation anim = AnimationUtils.loadAnimation ( context, android.R.anim.fade_in );

        anim.setStartOffset ( offset );

        anim.setAnimationListener ( new Animation.AnimationListener () {
            @Override
            public void onAnimationStart ( Animation animation )
            {
            }

            @Override
            public void onAnimationEnd ( Animation animation )
            {
                view.setVisibility ( View.VISIBLE );
            }

            @Override
            public void onAnimationRepeat ( Animation animation )
            {
            }
        } );

        view.startAnimation ( anim );
    }

    private void setDisappearAnimation ( Context context, final View view, int offset )
    {
        Animation disappear = AnimationUtils.loadAnimation (
                context, android.R.anim.fade_out
        );

        disappear.setStartOffset ( offset );

        disappear.setAnimationListener ( new Animation.AnimationListener () {
             @Override
             public void onAnimationStart ( Animation animation ) { }

             @Override
             public void onAnimationEnd ( Animation animation )
             {
                 view.setVisibility ( View.GONE );
             }

             @Override
             public void onAnimationRepeat ( Animation animation ) { }
         } );

        view.startAnimation ( disappear );
    }

    private void artFadeReplace ( final File file )
    {
        View v = getView ();

        if ( null == v )
            return;

        if ( file != null && file.getAbsolutePath ().equals ( last_cover ) )
            return;

        last_cover = null == file ? null : file.getAbsolutePath ();

        final ImageView view = (ImageView) v.findViewById ( R.id.img );

        final Animation disappear = AnimationUtils.loadAnimation (
                getActivity (), android.R.anim.fade_out
        );

        disappear.setStartOffset ( 0 );

        disappear.setAnimationListener ( new Animation.AnimationListener () {
             @Override
             public void onAnimationStart ( Animation animation ) { }

             @Override
             public void onAnimationEnd ( Animation animation )
             {
                 getActivity ().runOnUiThread (
                         new Runnable ()
                         {
                             @Override
                             public void run ()
                             {
                                 view.setVisibility ( View.GONE );

                                 if ( null != file )
                                 {
                                     view.setImageBitmap ( BitmapFactory.decodeFile (
                                                     file.getAbsolutePath ()
                                     ) );

                                     setAppearAnimation ( getActivity (), view, 0 );
                                 } else
                                 {
                                     view.setImageBitmap ( null );
                                 }
                             }
                         }
                 );
             }

             @Override
             public void onAnimationRepeat ( Animation animation ) { }
         } );

        getActivity ().runOnUiThread (
                new Runnable ()
                {
                    @Override
                    public void run ()
                    {
                        view.startAnimation ( disappear );
                    }
                }
        );
    }

    private void refreshInactiveData ( final View v )
    {
        TextView id = (TextView)v.findViewById ( R.id.id_mpris );
        id.setText ( m_info.id );
    }

    private View.OnClickListener m_click_listen = new View.OnClickListener () {

        Double last_volume = 0.0;

        @Override
        public void onClick ( View view )
        {
            switch ( view.getId () )
            {
                case R.id.launch:
                    signalClientCommand ( new ClientCommand ( Act.Launch, m_info.id ) );
                    break;
                case R.id.play:
                    signalClientCommand ( new ClientCommand ( Act.PlayPause, m_info.id ) );
                    break;
                case R.id.frev:
                    signalClientCommand ( new ClientCommand ( Act.FRev, m_info.id ) );
                    break;
                case R.id.ffwd:
                    signalClientCommand ( new ClientCommand ( Act.FFwd, m_info.id ) );
                    break;
                case R.id.mute:
                    signalClientCommand ( new ClientCommand ( Act.Volume, m_info.id, Double.toString ( last_volume ) ) );
                    last_volume = m_info.volume;
                    break;
                case R.id.shuffle:
                    signalClientCommand ( new ClientCommand ( Act.Shuffle, m_info.id, Boolean.toString ( !m_info.shuffle ) ) );
                    break;
                case R.id.loop:
                    LoopState state = LoopState.values () [ (m_info.loop.ordinal () + 1) % LoopState.values ().length ];
                    signalClientCommand ( new ClientCommand ( Act.Loop, m_info.id, state.toString () ) );
                    break;
                case R.id.fullscreen:
                    signalClientCommand ( new ClientCommand ( Act.Fullscreen, m_info.id, Boolean.toString ( !m_info.fullscreen ) ) );
                    break;
                case R.id.raise:
                    signalClientCommand ( new ClientCommand ( Act.Raise, m_info.id ) );
                    break;
                case R.id.playlists:
                    doPlaylistsPopup ();
                    break;
            }
        }
    };

    private synchronized void doPlaylistsPopup ()
    {
        if ( null == m_popup )
        {
            m_popup = new ListPopupWindow ( getActivity () );
            m_popup.setModal ( true );
            m_popup.setAnchorView ( getView ().findViewById ( R.id.controls ) );
            m_popup.setContentWidth ( getView ().getWidth () );
            m_popup.setOnItemClickListener ( m_listen_popup_click );
        }

        m_popup.show ();

        signalClientCommand (
                new ClientCommand (
                        Act.Playlists,
                        m_info.id,
                        PlaylistSort.Alphabetical.toString (),
                        Boolean.FALSE.toString ()
                ),
                new CommonDroid.OnServerResponseListener ()
                {
                    @Override
                    public void onServerResponse ( ServerResponse serverResponse )
                    {
                        final ArrayAdapter adapter = new ArrayAdapter (
                                getActivity (),
                                android.R.layout.simple_list_item_1,
                                serverResponse.playlists
                        );

                        getActivity ().runOnUiThread (
                                new Runnable ()
                                {
                                    @Override
                                    public void run ()
                                    {
                                        m_popup.setAdapter ( adapter );
                                        m_popup.show ();
                                    }
                                }
                        );
                    }
                }
        );
    }

    private ListPopupWindow m_popup;

    private AdapterView.OnItemClickListener m_listen_popup_click = new AdapterView.OnItemClickListener () {
        @Override
        public void onItemClick ( AdapterView< ? > adapterView, View view, int i, long l )
        {
            PlaylistInfo list = (PlaylistInfo) adapterView.getItemAtPosition ( i );

            signalClientCommand ( new ClientCommand ( Act.ActivatePlaylist, list.pid, list.id ) );

            m_popup.dismiss ();
        }
    };

    private SeekBar.OnSeekBarChangeListener m_seek_listen = new SeekBar.OnSeekBarChangeListener () {

        @Override
        public void onProgressChanged ( SeekBar seekBar, int i, boolean b ) { }

        @Override
        public void onStartTrackingTouch ( SeekBar seekBar )
        {
            seeking = R.id.seek == seekBar.getId ();
        }

        @Override
        public void onStopTrackingTouch ( SeekBar seekBar )
        {
            seeking = false;

            int end_seek = seekBar.getProgress ();

            switch ( seekBar.getId () )
            {
                case R.id.seek:
                    /*
                     * Calculate the correct position of the current track
                     */
                    int currseek = calculateSeek ();

                    Long dx = 1000L * (end_seek - currseek);

                    // Update the position data so the thread
                    // doesn't override us
                    last_info = System.currentTimeMillis ();
                    m_info.position = 1000L * end_seek;
                    signalClientCommand (
                            new ClientCommand ( Act.Seek, m_info.id, String.valueOf ( dx ) )
                    );
                    break;
                case R.id.volume:
                    signalClientCommand ( new ClientCommand ( Act.Volume, m_info.id, Double.toString ( end_seek / (double)100 ) ) );
                    break;
            }
        }
    };

    private class SeekUpdater extends TimerTask {

        @Override
        public void run ()
        {
            if ( !seeking )
            {
                final SeekBar sb = (SeekBar) getView ().findViewById ( R.id.seek );

                getActivity ().runOnUiThread ( new Runnable () {
                                                   @Override
                                                   public void run ()
                                                   {
                       sb.setProgress ( calculateSeek () );
                       }
                   } );

            }
        }
    }
}
