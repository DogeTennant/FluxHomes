package com.dogetennant.fluxhomes.database;

import com.dogetennant.fluxhomes.models.Home;

import java.util.List;
import java.util.UUID;

public abstract class DatabaseManager {

    public abstract void initialize();

    public abstract void shutdown();

    public abstract void saveHome(Home home);

    public abstract void deleteHome(UUID ownerUUID, String name);

    public abstract void deleteAllHomes(UUID ownerUUID);

    public abstract Home getHome(UUID ownerUUID, String name);

    public abstract List<Home> getHomes(UUID ownerUUID);

    public abstract int getHomeCount(UUID ownerUUID);
}