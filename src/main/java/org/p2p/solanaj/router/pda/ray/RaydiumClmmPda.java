package org.p2p.solanaj.router.pda.ray;

import org.p2p.solanaj.core.PublicKey;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Raydium CLMM PDA 工具类。
 */
public final class RaydiumClmmPda {

    /**
     * Raydium CLMM 主网 Program。
     */
    public static final PublicKey CLMM_PROGRAM_ID = new PublicKey("CAMMCzo5YL8w4VFF8KVHrK22GGUsp5VTaW7grrKgrWqK");

    /**
     * 一个 TickArray 固定包含 60 个 TickState。
     */
    public static final int TICK_ARRAY_SIZE = 60;


    /**
     * CLMM 中可以根据 PoolState 本地派生出来的一组账户。
     *
     * @param vault0                     token_mint_0 对应的 Pool Vault
     * @param vault1                     token_mint_1 对应的 Pool Vault
     * @param observation                Pool 对应的 Observation PDA
     * @param bitmapExtension            TickArray Bitmap Extension PDA
     * @param currentTickArrayStartIndex 当前 tick_current 所在 TickArray 的起始 Tick Index
     * @param currentTickArray           当前 tick_current 所在 TickArray PDA
     */
    public record DerivedAccounts(PublicKey vault0, PublicKey vault1, PublicKey observation, PublicKey bitmapExtension,
                                  int currentTickArrayStartIndex, PublicKey currentTickArray) {
    }


    /**
     * 根据已经从链上读取的 PoolState 数据，
     * 一次性生成当前 CLMM Pool 可以直接本地推导的账户。
     *
     * <p>
     * 不需要再次 RPC 查询这些地址。
     *
     * @param poolState   Raydium CLMM PoolState 地址。
     *                    <p>
     *                    注意：
     *                    这是承载 PoolState 数据的账户自身地址，
     *                    不是 PoolState data 内部的某个字段。
     * @param tokenMint0  PoolState.token_mint_0
     * @param tokenMint1  PoolState.token_mint_1
     * @param tickCurrent PoolState.tick_current
     *                    <p>
     *                    这是动态值，需要使用最新链上状态
     *                    或你本地实时维护的状态。
     * @param tickSpacing PoolState.tick_spacing
     *                    <p>
     *                    例如：
     *                    tickSpacing = 60
     * @return 本地派生出来的 Vault、Observation、
     * BitmapExtension 和当前 TickArray
     */
    public static DerivedAccounts derivePoolAccounts(PublicKey poolState, PublicKey tokenMint0, PublicKey tokenMint1, int tickCurrent, int tickSpacing) {

        /*
         * 生成 token_mint_0 对应的 Pool Vault。
         */
        PublicKey vault0 = derivePoolVault(poolState, tokenMint0);

        /*
         * 生成 token_mint_1 对应的 Pool Vault。
         */
        PublicKey vault1 = derivePoolVault(poolState, tokenMint1);

        /*
         * 生成 Observation PDA。
         */
        PublicKey observation = deriveObservation(poolState);

        /*
         * 生成 TickArray Bitmap Extension PDA。
         *
         * 注意：
         * 地址可以永远本地生成；
         * 但账户是否存在、里面 bitmap 是什么，
         * 仍然属于链上状态。
         */
        PublicKey bitmapExtension = deriveBitmapExtension(poolState);

        /*
         * 根据当前 tick_current 和 tick_spacing
         * 计算当前 TickArray 的 startTickIndex。
         */
        int currentTickArrayStartIndex = getTickArrayStartIndex(tickCurrent, tickSpacing);

        /*
         * 根据 startTickIndex 生成当前 TickArray PDA。
         */
        PublicKey currentTickArray = deriveTickArray(poolState, currentTickArrayStartIndex);

        return new DerivedAccounts(vault0, vault1, observation, bitmapExtension, currentTickArrayStartIndex, currentTickArray);
    }


    /**
     * 派生 Raydium CLMM Pool Vault。
     *
     * <p>
     * PDA Seeds：
     *
     * <pre>
     * [
     *     "pool_vault",
     *     poolState,
     *     tokenMint
     * ]
     * </pre>
     *
     * @param poolState CLMM PoolState 地址
     * @param tokenMint token_mint_0 或 token_mint_1
     * @return 对应 Token Vault PDA
     */
    public static PublicKey derivePoolVault(PublicKey poolState, PublicKey tokenMint) {

        return PublicKey.findProgramAddress(List.of("pool_vault".getBytes(StandardCharsets.UTF_8),

                poolState.toByteArray(),

                tokenMint.toByteArray()), CLMM_PROGRAM_ID).getAddress();
    }


    /**
     * 派生 Raydium CLMM Observation PDA。
     *
     * <p>
     * PDA Seeds：
     *
     * <pre>
     * [
     *     "observation",
     *     poolState
     * ]
     * </pre>
     *
     * @param poolState CLMM PoolState 地址
     * @return Observation PDA
     */
    public static PublicKey deriveObservation(PublicKey poolState) {

        return PublicKey.findProgramAddress(List.of("observation".getBytes(StandardCharsets.UTF_8),

                poolState.toByteArray()), CLMM_PROGRAM_ID).getAddress();
    }


    /**
     * 派生 Raydium CLMM TickArray Bitmap Extension PDA。
     *
     * <p>
     * PDA Seeds：
     *
     * <pre>
     * [
     *     "pool_tick_array_bitmap_extension",
     *     poolState
     * ]
     * </pre>
     *
     * @param poolState CLMM PoolState 地址
     * @return TickArray Bitmap Extension PDA
     */
    public static PublicKey deriveBitmapExtension(PublicKey poolState) {

        return PublicKey.findProgramAddress(List.of("pool_tick_array_bitmap_extension".getBytes(StandardCharsets.UTF_8),

                poolState.toByteArray()), CLMM_PROGRAM_ID).getAddress();
    }


    /**
     * 根据 tick_current 计算当前 TickArray 的起始 Tick Index。
     *
     * <p>
     * 一个 TickArray 包含 60 个 Tick。
     *
     * <p>
     * 一个 TickArray 实际覆盖的 Tick Index 范围大小：
     *
     * <pre>
     * tickSpacing * 60
     * </pre>
     *
     * <p>
     * 例如：
     *
     * <pre>
     * tickCurrent = 16315
     * tickSpacing = 60
     *
     * arraySpan =
     * 60 * 60
     * =
     * 3600
     *
     * startTickIndex =
     * floorDiv(16315, 3600) * 3600
     * =
     * 14400
     * </pre>
     *
     * <p>
     * 注意：
     * tickCurrent 可能是负数，
     * 所以必须使用 Math.floorDiv()，
     * 不能直接使用 Java 的 /。
     *
     * @param tickCurrent PoolState.tick_current
     * @param tickSpacing PoolState.tick_spacing
     * @return 当前 TickArray 的 startTickIndex
     */
    public static int getTickArrayStartIndex(int tickCurrent, int tickSpacing) {

        if (tickSpacing <= 0) {
            throw new IllegalArgumentException("tickSpacing 必须大于 0");
        }

        /*
         * 一个 TickArray 包含 60 个 TickState，
         * 相邻 TickState 相隔 tickSpacing。
         */
        int ticksPerArray = tickSpacing * TICK_ARRAY_SIZE;

        /*
         * 必须使用 floorDiv，
         * 否则负数 tick 会算错。
         */
        return Math.floorDiv(tickCurrent, ticksPerArray) * ticksPerArray;
    }


    /**
     * 派生指定 startTickIndex 对应的 TickArray PDA。
     *
     * <p>
     * PDA Seeds：
     *
     * <pre>
     * [
     *     "tick_array",
     *     poolState,
     *     startTickIndex 的 i32 Big Endian
     * ]
     * </pre>
     *
     * <p>
     * 特别注意：
     * <p>
     * startTickIndex 这里使用的是：
     *
     * <pre>
     * i32 Big Endian
     * </pre>
     * <p>
     * 不是 Little Endian。
     *
     * @param poolState      CLMM PoolState 地址
     * @param startTickIndex TickArray 起始 Tick Index
     * @return TickArray PDA
     */
    public static PublicKey deriveTickArray(PublicKey poolState, int startTickIndex) {

        /*
         * Raydium TickArray PDA 使用 i32 Big Endian。
         */
        byte[] startIndexBytes = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(startTickIndex).array();

        return PublicKey.findProgramAddress(List.of("tick_array".getBytes(StandardCharsets.UTF_8),

                poolState.toByteArray(),

                startIndexBytes), CLMM_PROGRAM_ID).getAddress();
    }


    /**
     * 派生 Reward Vault PDA。
     *
     * <p>
     * PDA Seeds：
     *
     * <pre>
     * [
     *     "pool_reward_vault",
     *     poolState,
     *     rewardMint
     * ]
     * </pre>
     *
     * @param poolState  CLMM PoolState 地址
     * @param rewardMint Reward Token Mint
     * @return Reward Vault PDA
     */
    public static PublicKey deriveRewardVault(PublicKey poolState, PublicKey rewardMint) {

        return PublicKey.findProgramAddress(List.of("pool_reward_vault".getBytes(StandardCharsets.UTF_8),

                poolState.toByteArray(),

                rewardMint.toByteArray()), CLMM_PROGRAM_ID).getAddress();
    }


}
