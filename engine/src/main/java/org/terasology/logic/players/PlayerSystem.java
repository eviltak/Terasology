/*
 * Copyright 2013 MovingBlocks
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.terasology.logic.players;

import com.google.common.collect.Lists;
import org.terasology.entitySystem.entity.EntityManager;
import org.terasology.entitySystem.entity.EntityRef;
import org.terasology.entitySystem.entity.lifecycleEvents.BeforeDeactivateComponent;
import org.terasology.entitySystem.entity.lifecycleEvents.OnActivatedComponent;
import org.terasology.entitySystem.event.ReceiveEvent;
import org.terasology.entitySystem.systems.BaseComponentSystem;
import org.terasology.entitySystem.systems.RegisterMode;
import org.terasology.entitySystem.systems.RegisterSystem;
import org.terasology.entitySystem.systems.UpdateSubscriberSystem;
import org.terasology.logic.characters.CharacterComponent;
import org.terasology.logic.console.commandSystem.annotations.Command;
import org.terasology.logic.console.commandSystem.annotations.CommandParam;
import org.terasology.logic.console.commandSystem.annotations.Sender;
import org.terasology.logic.health.DestroyEvent;
import org.terasology.logic.health.EngineDamageTypes;
import org.terasology.logic.health.HealthComponent;
import org.terasology.logic.location.Location;
import org.terasology.logic.location.LocationComponent;
import org.terasology.logic.permission.PermissionManager;
import org.terasology.logic.players.event.OnPlayerSpawnedEvent;
import org.terasology.logic.players.event.RespawnRequestEvent;
import org.terasology.math.Region3i;
import org.terasology.math.TeraMath;
import org.terasology.math.geom.Quat4f;
import org.terasology.math.geom.Vector3f;
import org.terasology.math.geom.Vector3i;
import org.terasology.network.Client;
import org.terasology.network.ClientComponent;
import org.terasology.network.NetworkSystem;
import org.terasology.network.events.ConnectedEvent;
import org.terasology.network.events.DisconnectedEvent;
import org.terasology.persistence.PlayerStore;
import org.terasology.registry.In;
import org.terasology.rendering.world.ViewDistance;
import org.terasology.rendering.world.WorldRenderer;
import org.terasology.world.WorldProvider;
import org.terasology.world.block.BlockManager;
import org.terasology.world.chunks.ChunkConstants;
import org.terasology.world.generation.Region;
import org.terasology.world.generation.World;
import org.terasology.world.generation.facets.SeaLevelFacet;
import org.terasology.world.generation.facets.SurfaceHeightFacet;
import org.terasology.world.generator.WorldGenerator;

import java.util.Iterator;
import java.util.List;

/**
 * @author Immortius
 */
@RegisterSystem(RegisterMode.AUTHORITY)
public class PlayerSystem extends BaseComponentSystem implements UpdateSubscriberSystem {

    @In
    private EntityManager entityManager;

    @In
    private WorldRenderer worldRenderer;
    @In
    private WorldGenerator worldGenerator;
    @In
    private WorldProvider worldProvider;

    @In
    private NetworkSystem networkSystem;

    private List<SpawningClientInfo> clientsPreparingToSpawn = Lists.newArrayList();

    @Override
    public void initialise() {
    }

    @Override
    public void update(float delta) {
        Iterator<SpawningClientInfo> i = clientsPreparingToSpawn.iterator();
        while (i.hasNext()) {
            SpawningClientInfo spawning = i.next();
            if (worldProvider.isBlockRelevant(spawning.position)) {
                if (spawning.playerStore == null) {
                    spawnPlayer(spawning.clientEntity);
                } else if (!spawning.playerStore.hasCharacter()) {
                    spawning.playerStore.restoreEntities();
                    spawnPlayer(spawning.clientEntity);
                } else {
                    restoreCharacter(spawning.clientEntity, spawning.playerStore);
                }
                i.remove();
            }
        }
    }

    private void spawnPlayer(EntityRef clientEntity) {

        Spawner spawner = worldGenerator.getSpawner();
        World world = worldGenerator.getWorld();
        Vector3f spawnPos = spawner.getSpawnPosition(world, clientEntity);

        ClientComponent client = clientEntity.getComponent(ClientComponent.class);
        if (client != null) {
            PlayerFactory playerFactory = new PlayerFactory(entityManager);
            float heightOffset = 1.5f;
            Vector3f spawnPosition = new Vector3f(spawnPos.x, spawnPos.y + heightOffset, spawnPos.z);
            EntityRef playerCharacter = playerFactory.newInstance(spawnPosition, clientEntity);
            Location.attachChild(playerCharacter, clientEntity, new Vector3f(), new Quat4f(0, 0, 0, 1));

            Client clientListener = networkSystem.getOwner(clientEntity);
            Vector3i distance = clientListener.getViewDistance().getChunkDistance();
            updateRelevanceEntity(clientEntity, distance);
            client.character = playerCharacter;
            clientEntity.saveComponent(client);
            playerCharacter.send(new OnPlayerSpawnedEvent());
        }
    }

    @ReceiveEvent(components = ClientComponent.class)
    public void onConnect(ConnectedEvent connected, EntityRef entity) {
        LocationComponent loc = entity.getComponent(LocationComponent.class);
        loc.setWorldPosition(connected.getPlayerStore().getRelevanceLocation());
        entity.saveComponent(loc);
        addRelevanceEntity(entity, ViewDistance.LEGALLY_BLIND.getChunkDistance(), networkSystem.getOwner(entity));
        if (connected.getPlayerStore().hasCharacter()) {
            if (worldProvider.isBlockRelevant(connected.getPlayerStore().getRelevanceLocation())) {
                restoreCharacter(entity, connected.getPlayerStore());
            } else {
                SpawningClientInfo spawningClientInfo = new SpawningClientInfo(entity, connected.getPlayerStore().getRelevanceLocation(), connected.getPlayerStore());
                clientsPreparingToSpawn.add(spawningClientInfo);
            }
        } else {
            Vector3i pos = Vector3i.zero();
            if (worldProvider.isBlockRelevant(pos)) {
                spawnPlayer(entity);
            } else {
                // Move the player (before it's spawned) to the spawn-position to make sure the relevance
                // loads the chunk at some point
                Spawner spawner = worldGenerator.getSpawner();
                World world = worldGenerator.getWorld();
                Vector3f spawnPosition = spawner.getSpawnPosition(world, entity);
                loc.setWorldPosition(spawnPosition);
                entity.saveComponent(loc);

                SpawningClientInfo spawningClientInfo = new SpawningClientInfo(entity, spawnPosition);
                clientsPreparingToSpawn.add(spawningClientInfo);
            }
        }
    }

    private void restoreCharacter(EntityRef entity, PlayerStore playerStore) {
        playerStore.restoreEntities();
        EntityRef character = playerStore.getCharacter();
        // TODO: adjust location to safe spot
        if (character == null) {
            spawnPlayer(entity);
        } else {
            Client clientListener = networkSystem.getOwner(entity);
            updateRelevanceEntity(entity, clientListener.getViewDistance().getChunkDistance());
            ClientComponent client = entity.getComponent(ClientComponent.class);
            client.character = character;
            entity.saveComponent(client);

            CharacterComponent characterComp = character.getComponent(CharacterComponent.class);
            if (characterComp != null) {
                characterComp.controller = entity;
                character.saveComponent(characterComp);
                character.setOwner(entity);
                Location.attachChild(character, entity, new Vector3f(), new Quat4f(0, 0, 0, 1));
            } else {
                character.destroy();
                spawnPlayer(entity);
            }
        }
    }

    private void updateRelevanceEntity(EntityRef entity, Vector3i chunkDistance) {
        //RelevanceRegionComponent relevanceRegion = new RelevanceRegionComponent();
        //relevanceRegion.distance = chunkDistance;
        //entity.saveComponent(relevanceRegion);
        worldRenderer.getChunkProvider().updateRelevanceEntity(entity, chunkDistance);
    }

    private void removeRelevanceEntity(EntityRef entity) {
        //entity.removeComponent(RelevanceRegionComponent.class);
        worldRenderer.getChunkProvider().removeRelevanceEntity(entity);
    }


    private void addRelevanceEntity(EntityRef entity, Vector3i chunkDistance, Client owner) {
        //RelevanceRegionComponent relevanceRegion = new RelevanceRegionComponent();
        //relevanceRegion.distance = chunkDistance;
        //entity.addComponent(relevanceRegion);
        worldRenderer.getChunkProvider().addRelevanceEntity(entity, chunkDistance, owner);
    }

    @ReceiveEvent(components = ClientComponent.class)
    public void onDisconnect(DisconnectedEvent event, EntityRef entity) {
        EntityRef character = entity.getComponent(ClientComponent.class).character;
        removeRelevanceEntity(entity);
    }

    @ReceiveEvent(components = {ClientComponent.class})
    public void onRespawnRequest(RespawnRequestEvent event, EntityRef entity) {
        ClientComponent client = entity.getComponent(ClientComponent.class);
        Spawner spawner = worldGenerator.getSpawner();
        World world = worldGenerator.getWorld();
        if (!client.character.exists()) {
            Vector3i pos = Vector3i.zero();
            if (worldProvider.isBlockRelevant(pos)) {
                spawnPlayer(entity);
            } else {
                LocationComponent loc = entity.getComponent(LocationComponent.class);

                Vector3f spawnPosition = spawner.getSpawnPosition(world, entity);
                loc.setWorldPosition(spawnPosition);
                entity.saveComponent(loc);
                updateRelevanceEntity(entity, ViewDistance.LEGALLY_BLIND.getChunkDistance());

                SpawningClientInfo info = new SpawningClientInfo(entity, spawnPosition);
                clientsPreparingToSpawn.add(info);
            }
        }
    }

    @Command(value = "kill", shortDescription = "Reduce the player's health to zero", runOnServer = true,
            requiredPermission = PermissionManager.NO_PERMISSION)
    public void killCommand(@Sender EntityRef client) {
        ClientComponent clientComp = client.getComponent(ClientComponent.class);
        HealthComponent health = clientComp.character.getComponent(HealthComponent.class);
        if (health != null) {
            clientComp.character.send(new DestroyEvent(clientComp.character, EntityRef.NULL, EngineDamageTypes.DIRECT.get()));
        }
    }

    @Command(value = "teleport", shortDescription = "Teleports you to a location", runOnServer = true,
            requiredPermission = PermissionManager.CHEAT_PERMISSION)
    public String teleportCommand(@Sender EntityRef sender, @CommandParam("x") float x, @CommandParam("y") float y, @CommandParam("z") float z) {
        ClientComponent clientComp = sender.getComponent(ClientComponent.class);
        LocationComponent location = clientComp.character.getComponent(LocationComponent.class);
        if (location != null) {
            // deactivate the character to reset the CharacterPredictionSystem,
            // which would overwrite the character location
            clientComp.character.send(BeforeDeactivateComponent.newInstance());

            location.setWorldPosition(new Vector3f(x, y, z));
            clientComp.character.saveComponent(location);

            // re-active the character
            clientComp.character.send(OnActivatedComponent.newInstance());
        }
        return "Teleported to " + x + " " + y + " " + z;
    }

    private static class SpawningClientInfo {
        public EntityRef clientEntity;
        public PlayerStore playerStore;
        public Vector3f position;

        public SpawningClientInfo(EntityRef client, Vector3f position) {
            this.clientEntity = client;
            this.position = position;
        }

        public SpawningClientInfo(EntityRef client, Vector3f position, PlayerStore playerStore) {
            this(client, position);
            this.playerStore = playerStore;
        }
    }
}
