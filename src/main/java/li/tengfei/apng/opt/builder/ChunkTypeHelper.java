package li.tengfei.apng.opt.builder;

import li.tengfei.apng.base.ApngConst;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

/**
 * Png Chunk Type Helper
 *
 * @author ltf
 * @since 16/12/7, 下午12:31
 */
public class ChunkTypeHelper {

    public static String getTypeName(int typeCode) {
        return Reverter.instance.getTypeName(typeCode);
    }

    private static class Reverter {
        private static Reverter instance = new Reverter();
        private Map<Integer, String> types = new HashMap<>();

        public Reverter() {
            for (Field field : ApngConst.class.getDeclaredFields()) {
                try {
                    types.put(field.getInt(null), field.getName().replace("CODE_",""));
                } catch (IllegalAccessException e) {
                }
            }
        }

        String getTypeName(int typeCode) {
            if (types.containsKey(typeCode))
                return types.get(typeCode);
            else
                return "Unkown-Type-Code: " + typeCode;
        }
    }
}
