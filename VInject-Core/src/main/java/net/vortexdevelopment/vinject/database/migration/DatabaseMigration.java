package net.vortexdevelopment.vinject.database.migration;

public interface DatabaseMigration {

    public void onMigration(); //TODO add context parameter to be able to create tables and insert data
}
