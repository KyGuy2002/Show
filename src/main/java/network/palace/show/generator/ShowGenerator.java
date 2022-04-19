package network.palace.show.generator;

import com.goebl.david.Request;
import com.goebl.david.Webb;
import com.google.gson.JsonObject;
import com.sk89q.util.StringUtil;
import network.palace.show.ShowPlugin;
import network.palace.show.actions.FakeBlockAction;
import org.apache.commons.lang.StringUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.*;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.List;
import java.util.UUID;

public class ShowGenerator {
    private final String ACCESS_TOKEN = ShowPlugin.getInstance().getGithubToken();
    private HashMap<UUID, GeneratorSession> generatorSessions = new HashMap<>();

    public GeneratorSession getSession(UUID uuid) {
        return generatorSessions.get(uuid);
    }

    public GeneratorSession getOrCreateSession(UUID uuid) {
        GeneratorSession session = getSession(uuid);
        if (session == null) {
            session = new GeneratorSession(uuid);
            addSession(session);
        }
        return session;
    }

    public void addSession(GeneratorSession session) {
        generatorSessions.put(session.getUuid(), session);
    }

    public void removeSession(UUID uuid) {
        generatorSessions.remove(uuid);
    }

    public String postGist(List<FakeBlockAction> actions, String name) throws Exception {
        Webb webb = Webb.create();

        JsonObject obj = new JsonObject();
        obj.addProperty("description", "Generated by Show v" + ShowPlugin.getInstance().getDescription().getVersion() + " on " + ShowPlugin.getInstance().getServerIp() + " at " + System.currentTimeMillis());
        obj.addProperty("public", "false");

        JsonObject files = new JsonObject();
        JsonObject file = new JsonObject();

        StringBuilder content = new StringBuilder();

        for (FakeBlockAction action : actions) {
            Location loc = action.getLoc();
            double time = ((int) ((action.getTime() / 1000.0) * 10)) / 10.0;
            Material mat = action.getData().getMaterial();
            int x = loc.getBlockX();
            int y = loc.getBlockY();
            int z = loc.getBlockZ();
            String actionString = time + "\u0009" + "FakeBlock" + "\u0009" + mat + "\u0009" + x + "," + y + "," + z + "\u0009" + getBlockDataString(action.getData());
            content.append(actionString).append("\n");
        }

        file.addProperty("content", content.toString());

        files.add(name + ".show", file);

        obj.add("files", files);

        System.out.println("SENDING (" + actions.size() + "): " + obj.toString());

        Request req = webb.post("https://api.github.com/gists")
                .header("Accept", "application/vnd.github.v3+json")
                .header("Authorization", "token " + ACCESS_TOKEN)
                .header("Content-Type", "application/json")
                .body(obj);

        JSONObject response = req.asJsonObject().getBody();

        return response.getString("html_url");
    }

    /*
    Whole Line:
     0      1      2       3             4
    TIME ACTION MATERIAL COORDS      BLOCK_DATA
    3.0	FakeBlock AIR	 14,5,1   STAIRS:DATA:DATA
    .
    .
    STAIRS,BOTTOM,NORTH-TRUE:EAST-FALSE:SOUTH-FALSE:WEST-FALSE,INNER_LEFT
    .
    .
    Block Data:
      0           1         2          3       4
    NONE
    STAIRS   :   HALF :   FACING  :  SHAPE           -> STAIRS:BOTTOM/TOP:NORTH/EAST/SOUTH/WEST:INNER_LEFT/...
    FENCE    :   FACE                                -> FENCE:NORTH/EAST/SOUTH/WEST ex) FENCE:NORTH-TRUE:SOUTH-FALSE
    GLASS_PANE : FACE                                -> GLASS_PANE:NORTH/EAST/SOUTH/WEST ex) GLASS_PANE:NORTH-TRUE:SOUTH-FALSE
    TRAPDOOR  :  HALF :   FACING  :  OPEN            -> TRAPDOOR:BOTTOM/TOP:NORTH/EAST/SOUTH/WEST:TRUE/FALSE
    DOOR     :   HALF  :  FACING  :  OPEN  :  HINGE  -> DOOR:BOTTOM/TOP:NORTH/EAST/SOUTH/WEST:TRUE/FALSE:LEFT/RIGHT
     */
    private String getBlockDataString(BlockData blockData) {
        String dataString = "NONE";

        if (blockData instanceof Stairs) {
            String half = ((Stairs) blockData).getHalf().toString();
            String facing = ((Stairs) blockData).getFacing().toString();
            String shape = ((Stairs) blockData).getShape().toString();
            dataString = "STAIRS," + half.toUpperCase() + "," + facing.toUpperCase() + "," + shape.toUpperCase();

        } else if (blockData instanceof Fence) {
            dataString = "FENCE,";

            // True for included
            for (BlockFace face : ((Fence) blockData).getFaces()) {
                dataString = dataString + face.toString().toUpperCase() + "-TRUE:";
            }

            // False for others
            if (!dataString.contains("NORTH")) dataString = dataString + "NORTH-FALSE:";
            if (!dataString.contains("EAST")) dataString = dataString + "EAST-FALSE:";
            if (!dataString.contains("SOUTH")) dataString = dataString + "SOUTH-FALSE:";
            if (!dataString.contains("WEST")) dataString = dataString + "WEST-FALSE:";

            // Remove last character
            dataString = StringUtils.chop(dataString);

        } else if (blockData instanceof GlassPane) { // TODO what does it do with non-stained glass pain?
            dataString = "GLASS_PANE,";

            // True for included
            for (BlockFace face : ((GlassPane) blockData).getFaces()) {
                dataString = dataString + face.toString().toUpperCase() + "-TRUE:";
            }

            // False for others
            if (!dataString.contains("NORTH")) dataString = dataString + "NORTH-FALSE:";
            if (!dataString.contains("EAST")) dataString = dataString + "EAST-FALSE:";
            if (!dataString.contains("SOUTH")) dataString = dataString + "SOUTH-FALSE:";
            if (!dataString.contains("WEST")) dataString = dataString + "WEST-FALSE:";

            // Remove last character
            dataString = StringUtils.chop(dataString);

        } else if (blockData instanceof TrapDoor) {
            String half = ((TrapDoor) blockData).getHalf().toString();
            String facing = ((TrapDoor) blockData).getFacing().toString();
            String open = String.valueOf(((TrapDoor) blockData).isOpen());
            dataString = "TRAPDOOR," + half.toUpperCase() + "," + facing.toUpperCase() + "," + open.toUpperCase();

        } else if (blockData instanceof Door) {
            String half = ((Door) blockData).getHalf().toString();
            String facing = ((Door) blockData).getFacing().toString();
            String open = String.valueOf(((Door) blockData).isOpen());
            String hinge = ((Door) blockData).getHinge().toString();
            dataString = "DOOR," + half.toUpperCase() + "," + facing.toUpperCase() + "," + open.toUpperCase() + "," + hinge.toUpperCase();

        }

        return dataString;
    }
}
