package li.tengfei.apng.opt.builder

import li.tengfei.apng.base.ApngConst
import li.tengfei.apng.ext.DIntWriter
import org.testng.Assert
import org.testng.annotations.Test

import static li.tengfei.apng.opt.optimizer.ChunkTypeHelper.*

/**
 *
 * @author ltf
 * @since 16/12/11, 下午1:46
 */
class ChunkTypeHelperTest {

    @Test
    void testGetTypeHash() {
        ArrayList<Integer> exists = new ArrayList<>();
        ApngConst.getDeclaredFields().each { exists.add(it.getInt(null)) }
        println "all type count: " + exists.size()

        ApngConst.getDeclaredFields().each {
            int typeCode = it.getInt(null)
            boolean canPatch = canUsePatch(typeCode)
            int typeHash = getTypeHash(typeCode, exists)
            DIntWriter dInt = new DIntWriter(typeHash)
            println String.format(
                    "%s : %b, %d, %d",
                    getTypeName(typeCode),
                    canPatch,
                    dInt.size,
                    typeHash
            )
            Assert.assertEquals(1, dInt.size)
        }

    }
}
