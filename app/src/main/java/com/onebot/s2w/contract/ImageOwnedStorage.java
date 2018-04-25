package com.onebot.s2w.contract;


import de.petendi.ethereum.android.contract.PendingTransaction;


public interface ImageOwnedStorage {

    String currentOwner();

    String getUrl();

    PendingTransaction<Void> setUrl(String data);

    int getVotes();

    PendingTransaction<Void> voteForUrl();
}
