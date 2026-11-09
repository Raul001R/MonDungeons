package sauceology.mondungeon;

/** Stored in the same player NBT as the consumed key and item reward. */
public interface DungeonPayoutReceipt {
    String mondungeonsGetReceipt();
    void mondungeonsSetReceipt(String receipt);
}
