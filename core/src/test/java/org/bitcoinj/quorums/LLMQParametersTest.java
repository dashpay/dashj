/*
 * Copyright 2021 Dash Core Group
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.bitcoinj.quorums;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class LLMQParametersTest {

    @Test
    public void verifyLLMQParameters() {
        LLMQParameters llmq_test = new LLMQParameters(LLMQParameters.LLMQType.LLMQ_TEST, "llmq_test",
                3, 2, 2, 24, 2, 10,
                18, 2, 2, 3, 3);
        // TODO: signingActiveQuorumCount is set to 2 for the malort devnet, the original value is 3
        LLMQParameters llmq_devnet = new LLMQParameters(LLMQParameters.LLMQType.LLMQ_DEVNET, "llmq_devnet",
                12, 7, 6, 24, 2, 10,
                18, 7, 2, 4, 6);
        LLMQParameters llmq50_60 = new LLMQParameters(LLMQParameters.LLMQType.LLMQ_50_60, "llmq_50_60",
                50, 40, 30, 24, 2, 10,
                18,40, 24, 25, 25);
        LLMQParameters llmq400_60 = new LLMQParameters(LLMQParameters.LLMQType.LLMQ_400_60, "llmq_400_60",
                400, 300, 240, 24*12, 4, 20,
                28, 300, 4, 5, 100);
        LLMQParameters llmq400_85 = new LLMQParameters(LLMQParameters.LLMQType.LLMQ_400_85, "llmq_400_85",
                400, 350, 340, 24 * 24, 4, 20,
                48, 300, 4, 5, 100);
        LLMQParameters llmq100_67 = new LLMQParameters(LLMQParameters.LLMQType.LLMQ_100_67, "llmq_100_67",
                100, 800, 67, 2, 2, 10,
                18, 80, 24, 25, 50);

        assertEquals(llmq50_60, LLMQParameters.fromType(LLMQParameters.LLMQType.LLMQ_50_60));
        assertEquals(llmq400_60, LLMQParameters.fromType(LLMQParameters.LLMQType.LLMQ_400_60));
        assertEquals(llmq400_85, LLMQParameters.fromType(LLMQParameters.LLMQType.LLMQ_400_85));
        assertEquals(llmq_devnet, LLMQParameters.fromType(LLMQParameters.LLMQType.LLMQ_DEVNET));
        assertEquals(llmq_test, LLMQParameters.fromType(LLMQParameters.LLMQType.LLMQ_TEST));
        assertEquals(llmq100_67, LLMQParameters.fromType(LLMQParameters.LLMQType.LLMQ_100_67));
    }

    @Test
    public void verifyAllQuorumTypes() {
        for (LLMQParameters.LLMQType type : LLMQParameters.LLMQType.values()) {
            if (type != LLMQParameters.LLMQType.LLMQ_NONE) {
                assertEquals(type, LLMQParameters.fromType(type).type);
            }
        }
    }
}
