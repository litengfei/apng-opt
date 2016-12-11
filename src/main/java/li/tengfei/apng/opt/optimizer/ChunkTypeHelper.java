package li.tengfei.apng.opt.optimizer;

import li.tengfei.apng.base.AngPatch;
import li.tengfei.apng.base.ApngConst;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import static li.tengfei.apng.base.ApngConst.CODE_IHDR;
import static li.tengfei.apng.base.ApngReader.COPIED_TYPE_CODES;

/**
 * Png Chunk Type Helper
 *
 * @author ltf
 * @since 16/12/7, 下午12:31
 */
public class ChunkTypeHelper {

    /**
     * get type name of a type code
     */
    public static String getTypeName(int typeCode) {
        return Reverter.instance.getTypeName(typeCode);
    }

    /**
     * test if this typeCode can use patch feature
     */
    public static boolean canUsePatch(int typeCode) {
        if (typeCode == CODE_IHDR) {
            return true;
        }
        return Arrays.binarySearch(COPIED_TYPE_CODES, typeCode) >= 0;
    }

    /**
     * get a typeCode's hash with follow rules:
     * 1. the hash can identify the typeCode in all previous existsTypes
     * 2. as short as possible
     */
    public static int getTypeHash(int typeCode, Collection<Integer> existsTypes) {
        int[] hashes = TypeHashMapper.instance.hashes(typeCode);
        for (int idx = 0; idx < hashes.length; idx++) {
            boolean ident = true;
            for (int existsType : existsTypes) {
                if (existsType != typeCode && hashes[idx] == TypeHashMapper.instance.hashes(existsType)[idx]) {
                    ident = false;
                    break;
                }
            }

            if (ident) return hashes[idx];
        }
        throw new IllegalStateException("Can find identity hash for typeCode!!");
    }

    private static class TypeHashMapper {
        private static TypeHashMapper instance = new TypeHashMapper();

        private Map<Integer, int[]> map = new HashMap<>();

        int[] hashes(int typeCode) {
            int[] hashes = map.get(typeCode);
            if (hashes == null) {
                hashes = AngPatch.typeCodeHashes(typeCode);
                map.put(typeCode, hashes);
            }
            return hashes;
        }
    }

    private static class Reverter {
        private static Reverter instance = new Reverter();
        private Map<Integer, String> types = new HashMap<>();

        public Reverter() {
            for (Field field : ApngConst.class.getDeclaredFields()) {
                try {
                    types.put(field.getInt(null), field.getName().replace("CODE_", ""));
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
