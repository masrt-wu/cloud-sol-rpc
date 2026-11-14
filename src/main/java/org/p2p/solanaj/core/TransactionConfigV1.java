package org.p2p.solanaj.core;

public class TransactionConfigV1 {

    // 整笔交易的 priority fee，单位 lamports
    private Long priorityFeeLamports;

    // CU limit
    private Integer computeUnitLimit;

    // loaded accounts data size
    private Integer loadedAccountsDataSizeLimit;

    // heap size
    private Integer heapSize;

    public Long getPriorityFeeLamports() {
        return priorityFeeLamports;
    }

    public TransactionConfigV1 setPriorityFeeLamports(long value) {
        this.priorityFeeLamports = value;
        return this;
    }

    public Integer getComputeUnitLimit() {
        return computeUnitLimit;
    }

    public TransactionConfigV1 setComputeUnitLimit(int value) {
        this.computeUnitLimit = value;
        return this;
    }

    public Integer getLoadedAccountsDataSizeLimit() {
        return loadedAccountsDataSizeLimit;
    }

    public TransactionConfigV1 setLoadedAccountsDataSizeLimit(int value) {
        this.loadedAccountsDataSizeLimit = value;
        return this;
    }

    public Integer getHeapSize() {
        return heapSize;
    }

    public TransactionConfigV1 setHeapSize(int value) {
        this.heapSize = value;
        return this;
    }
}