package org.p2p.solanaj.utils.tran;


import lombok.Data;

import java.util.List;

@Data
public class TranMetaVo {

    private List<String > logMessages;

    private List<Long> postBalances;


}
