package little.nj;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.stream.JsonReader;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.util.Arrays;

import static little.nj.CommonComponents.ClientCommand;
import static little.nj.CommonComponents.ServerResponse;

public class MockClient
{
    Socket sock;

    OutputStream m_os;
    InputStream m_is;

    Thread m_listen;

    Gson gson;

    public MockClient ( int port ) throws IOException
    {
        sock = new Socket ( "localhost", port );

        m_os = sock.getOutputStream ();
        m_is = sock.getInputStream ();

        gson = new GsonBuilder ().setPrettyPrinting ().create ();

        listen ();

        try
        {
            post ( new ClientCommand ( CommonComponents.Act.Players ));
            Thread.sleep ( 2000 );
        }
        catch (InterruptedException ie)
        {

        }
    }

    private void post ( ClientCommand comm ) throws IOException
    {
        m_os.write ( gson.toJson ( comm ).getBytes () );
    }

    private void listen ()
    {
        m_listen = new Thread (
                new Runnable ()
                {
                    @Override
                    public void run ()
                    {
                        JsonReader reader = new JsonReader ( new InputStreamReader ( m_is ) );
                        reader.setLenient ( true );
                        ServerResponse response;
                        do
                        {
                            try
                            {
                                response = gson.fromJson ( reader, ServerResponse.class );

                                System.out.printf (
                                        "Response: Type = '%s', Valid = '%s'%n", response.resp, response.valid
                                );

                                switch ( response.resp )
                                {
                                    case PlayerInfo:
                                        System.out.println ( Arrays.deepToString ( response.players ) );
                                        break;
                                    case Directory:
                                        CommonComponents.DirectoryToken tok = response.directory;
                                        System.out.printf (
                                                "%s:%s%n%s%n",
                                                tok.parent,
                                                tok.name,
                                                Arrays.deepToString ( tok.children )
                                        );
                                        break;
                                }
                            }
                            catch ( Exception ie )
                            {
                                ie.printStackTrace ();
                            }
                        } while ( !Thread.interrupted () );
                    }
                }
        );

        m_listen.start ();
    }
}
