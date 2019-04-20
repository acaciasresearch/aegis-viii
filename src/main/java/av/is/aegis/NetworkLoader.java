package av.is.aegis;

import java.io.*;

public class NetworkLoader {

    private final File file;

    private Network loadedNetwork;

    public NetworkLoader(File file) {
        this.file = file;
    }

    public NetworkForm load() {
        if(loadedNetwork != null) {
            return loadedNetwork;
        }
        try {
            loadedNetwork = (Network) deserialize();
            loadedNetwork.setLoaded();
            return loadedNetwork;
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
        return null;
    }

    public void start() {
        Network network = (Network) load();
        network.startFromLoaded();
    }

    private Object deserialize() throws IOException, ClassNotFoundException {
        try(FileInputStream stream = new FileInputStream(file)) {
            try(ObjectInputStream objectInputStream = new ObjectInputStream(stream)) {
                return objectInputStream.readObject();
            }
        }
    }
}
