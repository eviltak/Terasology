/*
 * Copyright 2018 MovingBlocks
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

import com.google.common.collect.Sets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.terasology.engine.Time;
import org.terasology.entitySystem.Component;
import org.terasology.entitySystem.entity.EntityBuilder;
import org.terasology.entitySystem.entity.EntityManager;
import org.terasology.entitySystem.entity.EntityRef;
import org.terasology.entitySystem.entity.lifecycleEvents.OnActivatedComponent;
import org.terasology.entitySystem.entity.lifecycleEvents.OnChangedComponent;
import org.terasology.entitySystem.event.ReceiveEvent;
import org.terasology.entitySystem.systems.BaseComponentSystem;
import org.terasology.entitySystem.systems.RegisterMode;
import org.terasology.entitySystem.systems.RegisterSystem;
import org.terasology.entitySystem.systems.UpdateSubscriberSystem;
import org.terasology.logic.characters.CharacterComponent;
import org.terasology.logic.characters.CharacterHeldItemComponent;
import org.terasology.logic.console.commandSystem.annotations.Command;
import org.terasology.logic.console.commandSystem.annotations.CommandParam;
import org.terasology.logic.location.Location;
import org.terasology.logic.location.LocationComponent;
import org.terasology.math.TeraMath;
import org.terasology.math.geom.Quat4f;
import org.terasology.math.geom.Vector3f;
import org.terasology.network.ClientComponent;
import org.terasology.registry.In;
import org.terasology.rendering.logic.VisualComponent;

import java.util.Iterator;
import java.util.Set;

/**
 * This client system handles displaying held items for all remote players in multiplayer session.
 * TODO known issue: after reconnecting, other players can appear to be holding no item until first action
 * TODO (including mouse click actions)
 */
@RegisterSystem(RegisterMode.CLIENT)
public class ThirdPersonRemoteClientSystem extends BaseComponentSystem implements UpdateSubscriberSystem {
    private static final Logger logger = LoggerFactory.getLogger(ThirdPersonRemoteClientSystem.class);

    private static final int USEANIMATIONLENGTH = 200;

    @In // TODO: localPlayer doesn't seem to work properly in headless multiplayer. Ends up connected to a NullEntityRef. So can't test against it
    private LocalPlayer localPlayer;

    @In
    private LocalPlayerSystem localPlayerSystem;

    @In
    private EntityManager entityManager;

    @In
    private Time time;

    /**
     * Ensures held item mount point entity exists, attaches it to the character and sets its transform.
     * @param event the activation that triggered the need to consider changing a held item
     * @param character the character for which we need to consider the held item
     * @param remotePersonHeldItemMountPointComponent data for the mount point on the remote character
     */
    @ReceiveEvent
    public void ensureClientSideEntityOnHeldItemMountPoint(OnActivatedComponent event, EntityRef character,
                                                           RemotePersonHeldItemMountPointComponent remotePersonHeldItemMountPointComponent) {
        if (relatesToLocalPlayer(character)) {
            logger.info("ensureClientSideEntityOnHeldItemMountPoint found its given character to relate to the local player, ignoring: {}", character);
            return;
        }

        // In case we haven't dealt with a given remote player yet set up a non-persistent mount point
        if (!remotePersonHeldItemMountPointComponent.mountPointEntity.exists()) {
            EntityBuilder builder = entityManager.newBuilder("engine:RemotePersonHeldItemMountPoint");
            builder.setPersistent(false);
            remotePersonHeldItemMountPointComponent.mountPointEntity = builder.build();
            character.saveComponent(remotePersonHeldItemMountPointComponent);
        }

        // Link the mount point entity to the camera
        Location.removeChild(character, remotePersonHeldItemMountPointComponent.mountPointEntity);
        Location.attachChild(character, remotePersonHeldItemMountPointComponent.mountPointEntity,
                remotePersonHeldItemMountPointComponent.translate,
                new Quat4f(
                        TeraMath.DEG_TO_RAD * remotePersonHeldItemMountPointComponent.rotateDegrees.y,
                        TeraMath.DEG_TO_RAD * remotePersonHeldItemMountPointComponent.rotateDegrees.x,
                        TeraMath.DEG_TO_RAD * remotePersonHeldItemMountPointComponent.rotateDegrees.z),
                remotePersonHeldItemMountPointComponent.scale);

    }

    @ReceiveEvent
    public void ensureHeldItemIsMountedOnLoad(OnChangedComponent event, EntityRef clientEntity, ClientComponent clientComponent) {
        if (relatesToLocalPlayer(clientEntity)) {
            logger.info("ensureHeldItemIsMountedOnLoad found its given clientEntity to relate to the local player, ignoring: {}", clientEntity);
            return;
        }

        if (clientEntity.exists() && clientComponent.character != EntityRef.NULL) {
            logger.info("ensureHeldItemIsMountedOnLoad says a given clientEntity exists, has a character, and isn't related to the local player: {}", clientEntity);
            CharacterHeldItemComponent characterHeldItemComponent = clientComponent.character.getComponent(CharacterHeldItemComponent.class);
            if (characterHeldItemComponent != null && !(clientComponent.character.equals(localPlayer.getCharacterEntity()))) {
                linkHeldItemLocationForRemotePlayer(characterHeldItemComponent.selectedItem, clientComponent.character);
            }
        } else {
            logger.info("ensureHeldItemIsMountedOnLoad given a remote client, but one that didn't properly exist?");
        }
    }

    @Command(shortDescription = "Sets the held item mount point translation for remote characters")
    public void setRemotePlayersHeldItemMountPointTranslations(@CommandParam("x") float x, @CommandParam("y") float y, @CommandParam("z") float z) {
        for (EntityRef remotePlayer : entityManager.getEntitiesWith(CharacterComponent.class, PlayerCharacterComponent.class)) {
            if (relatesToLocalPlayer(remotePlayer)) {
                RemotePersonHeldItemMountPointComponent newComponent = remotePlayer.getComponent(RemotePersonHeldItemMountPointComponent.class);
                if (newComponent != null) {
                    newComponent.translate = new Vector3f(x, y, z);
                    ensureClientSideEntityOnHeldItemMountPoint(OnActivatedComponent.newInstance(), remotePlayer, newComponent);
                }
            }
        }
    }

    @Command(shortDescription = "Sets the held item mount point rotation for remote characters")
    public void setRemotePlayersHeldItemMountPointRotations(@CommandParam("x") float x, @CommandParam("y") float y, @CommandParam("z") float z) {
        for (EntityRef remotePlayer : entityManager.getEntitiesWith(CharacterComponent.class, PlayerCharacterComponent.class)) {
            if (relatesToLocalPlayer(remotePlayer)) {
                RemotePersonHeldItemMountPointComponent newComponent = remotePlayer.getComponent(RemotePersonHeldItemMountPointComponent.class);
                if (newComponent != null) {
                    newComponent.rotateDegrees = new Vector3f(x, y, z);
                    ensureClientSideEntityOnHeldItemMountPoint(OnActivatedComponent.newInstance(), remotePlayer, newComponent);
                }
            }
        }
    }

    @Command(shortDescription = "Sets the held item mount point scale for remote characters")
    public void setRemotePlayersHeldItemMountPointScale(@CommandParam("scale") float scale) {
        for (EntityRef remotePlayer : entityManager.getEntitiesWith(CharacterComponent.class, PlayerCharacterComponent.class)) {
            if (relatesToLocalPlayer(remotePlayer)) {
                RemotePersonHeldItemMountPointComponent newComponent = remotePlayer.getComponent(RemotePersonHeldItemMountPointComponent.class);
                if (newComponent != null) {
                    newComponent.scale = scale;
                    ensureClientSideEntityOnHeldItemMountPoint(OnActivatedComponent.newInstance(), remotePlayer, newComponent);
                }
            }
        }
    }

    @ReceiveEvent
    public void onHeldItemActivated(OnActivatedComponent event, EntityRef player, CharacterHeldItemComponent heldItemComponent, CharacterComponent characterComponents) {
        if (relatesToLocalPlayer(player)) {
            logger.info("onHeldItemActivated found its given player to relate to the local player, ignoring: {}", player);
            return;
        }

        logger.info("onHeldItemActivated says the given player is not the local player's character entity: {}", player);
        EntityRef newItem = heldItemComponent.selectedItem;
        linkHeldItemLocationForRemotePlayer(newItem, player);
    }

    @ReceiveEvent
    public void onHeldItemChanged(OnChangedComponent event, EntityRef character, CharacterHeldItemComponent heldItemComponent, CharacterComponent characterComponents) {
        if (relatesToLocalPlayer(character)) {
            logger.info("onHeldItemChanged found its given character to relate to the local player, ignoring: {}", character);
            return;
        }

        logger.info("onHeldItemChanged says the given character is not the local player's character entity: {}", character);
        EntityRef newItem = heldItemComponent.selectedItem;
        linkHeldItemLocationForRemotePlayer(newItem, character);
    }

    /**
     * Changes held item entity.
     *
     * Detaches old held item and removes its components. Adds components to new held item and
     * attaches it to the mount point entity.
     */
    private void linkHeldItemLocationForRemotePlayer(EntityRef newItem, EntityRef player) {
        if (newItem == EntityRef.NULL) {
            logger.info("linkHeldItemLocationForRemotePlayer called with no item (empty hand) so skipping for player {}", player);
            return;
        }

        if (relatesToLocalPlayer(player)) {
           logger.info("linkHeldItemLocationForRemotePlayer called with an entity that relates to the local player, ignoring{}", player);
           return;
        }

        // Find out if there is a current held item that maps to this player
        EntityRef currentHeldItem = EntityRef.NULL;
        for (EntityRef heldItemCandidate : entityManager.getEntitiesWith(ItemIsRemotelyHeldComponent.class)) {
            EntityRef remotePlayerCandidate = heldItemCandidate.getComponent(ItemIsRemotelyHeldComponent.class).remotePlayer;
            logger.info("For held item candidate {} got its player candidate as {}", heldItemCandidate, remotePlayerCandidate);
            if (remotePlayerCandidate.equals(player)) {
                logger.info("Thinking we found a match with player {} so counting this held item as relevant for processing", player);
                currentHeldItem = heldItemCandidate;
            }
        }

        if (newItem != null && !newItem.equals(currentHeldItem)) {
            RemotePersonHeldItemMountPointComponent mountPointComponent = player.getComponent(RemotePersonHeldItemMountPointComponent.class);
            if (mountPointComponent != null) {

                //currentHeldItem is at this point the old item
                if (currentHeldItem != EntityRef.NULL) {
                    currentHeldItem.destroy();
                }

                // create client side held item entity for remote player and store it in local remote players map
                currentHeldItem = entityManager.create();
                logger.info("linkHeldItemLocationForRemotePlayer is now creating a new held item {}", currentHeldItem);

                // add the visually relevant components
                for (Component component : newItem.iterateComponents()) {
                    if (component instanceof VisualComponent && !(component instanceof FirstPersonHeldItemTransformComponent)) {
                        currentHeldItem.addComponent(component);
                    }
                }

                // ensure world location is set
                currentHeldItem.addComponent(new LocationComponent());

                // Map this held item to the player it is held by
                ItemIsRemotelyHeldComponent itemIsRemotelyHeldComponent = new ItemIsRemotelyHeldComponent();
                itemIsRemotelyHeldComponent.remotePlayer = player;
                currentHeldItem.addComponent(itemIsRemotelyHeldComponent);

                RemotePersonHeldItemTransformComponent heldItemTransformComponent = currentHeldItem.getComponent(RemotePersonHeldItemTransformComponent.class);
                if (heldItemTransformComponent == null) {
                    heldItemTransformComponent = new RemotePersonHeldItemTransformComponent();
                    currentHeldItem.addComponent(heldItemTransformComponent);
                }

                Location.attachChild(mountPointComponent.mountPointEntity, currentHeldItem,
                        heldItemTransformComponent.translate,
                        new Quat4f(
                                TeraMath.DEG_TO_RAD * heldItemTransformComponent.rotateDegrees.y,
                                TeraMath.DEG_TO_RAD * heldItemTransformComponent.rotateDegrees.x,
                                TeraMath.DEG_TO_RAD * heldItemTransformComponent.rotateDegrees.z),
                        heldItemTransformComponent.scale);
            }
        }
    }

    /**
     * modifies the remote players' held item mount points to show and move their held items at their location
     */
    @Override
    public void update(float delta) {

        // Make a set of all held items that exist so we can review them and later toss any no longer needed
        Set<EntityRef> heldItemsForReview = Sets.newHashSet(entityManager.getEntitiesWith(ItemIsRemotelyHeldComponent.class));
        logger.info("As we start update we got the following known remote items to consider: {}", heldItemsForReview);

        // Note that the inclusion of PlayerCharacterComponent excludes "characters" like Gooey. In the future such critters may also want held items
        for (EntityRef remotePlayer : entityManager.getEntitiesWith(CharacterComponent.class, PlayerCharacterComponent.class)) {
            if (relatesToLocalPlayer(remotePlayer)) {
                logger.info("ThirdPersonRemoteClientSystem's update is ignoring the local player: {}", remotePlayer);
                continue;
            }

            // Find the associated held item for this player, if one exists
            EntityRef currentHeldItem = EntityRef.NULL;
            Iterator<EntityRef> heldItermsIterator = heldItemsForReview.iterator();
            while (heldItermsIterator.hasNext()) {
                EntityRef heldItemCandidate = heldItermsIterator.next();
                logger.info("Checking heldItemCandidate {} for remote player {}", heldItemCandidate, remotePlayer);
                ItemIsRemotelyHeldComponent itemIsRemotelyHeldComponent = heldItemCandidate.getComponent(ItemIsRemotelyHeldComponent.class);
                logger.info("The held item's owner is {}", itemIsRemotelyHeldComponent.remotePlayer);
                if (itemIsRemotelyHeldComponent.remotePlayer.equals(remotePlayer)) {
                    logger.info("Looks like we got a match for an existing item! Removing it from the set and processing it");
                    currentHeldItem = heldItemCandidate;
                    heldItermsIterator.remove();
                    break;
                }
            }

            logger.info("After searching for an existing held item the set is now: {}", heldItemsForReview);

            // get the remote person mount point
            CharacterHeldItemComponent characterHeldItemComponent = remotePlayer.getComponent(CharacterHeldItemComponent.class);
            RemotePersonHeldItemMountPointComponent mountPointComponent = remotePlayer.getComponent(RemotePersonHeldItemMountPointComponent.class);
            if (characterHeldItemComponent == null || mountPointComponent == null) {
                continue;
            }

            LocationComponent locationComponent = mountPointComponent.mountPointEntity.getComponent(LocationComponent.class);
            if (locationComponent == null) {
                continue;
            }

            long timeElapsedSinceLastUsed = time.getGameTimeInMs() - characterHeldItemComponent.lastItemUsedTime;
            float animateAmount = 0f;
            if (timeElapsedSinceLastUsed < USEANIMATIONLENGTH) {
                //TODO add other easing functions into utilities and use here?
                // half way through the animation will be the maximum extent of rotation and translation
                animateAmount = 1f - Math.abs(((float) timeElapsedSinceLastUsed / (float) USEANIMATIONLENGTH) - 0.5f);
            }
            float addPitch = 15f * animateAmount;
            float addYaw = 10f * animateAmount;
            locationComponent.setLocalRotation(new Quat4f(
                    TeraMath.DEG_TO_RAD * (mountPointComponent.rotateDegrees.y + addYaw),
                    TeraMath.DEG_TO_RAD * (mountPointComponent.rotateDegrees.x + addPitch),
                    TeraMath.DEG_TO_RAD * mountPointComponent.rotateDegrees.z));
            Vector3f offset = new Vector3f(0.05f * animateAmount, -0.24f * animateAmount, 0f);
            offset.add(mountPointComponent.translate);
            locationComponent.setLocalPosition(offset);

            mountPointComponent.mountPointEntity.saveComponent(locationComponent);
        }

        heldItemsForReview.forEach(remainingHeldItem -> {
            logger.info("After processing held items still had {} - destroying it", remainingHeldItem);
            if (remainingHeldItem.exists()) {
                remainingHeldItem.destroy();
            }
        });
    }

    @Override
    public void preSave() {

    }

    @Override
    public void shutdown() {
        /* TODO: Unsure if this would matter - seems to cause an NPE from engaging in destruction while already shutting down
        for (EntityRef remainingHeldItems : entityManager.getEntitiesWith(ItemIsRemotelyHeldComponent.class)) {
            if (remainingHeldItems.exists()) {
                remainingHeldItems.destroy();
            }
        }
        */
    }

    /**
     * Checks a given entity in a variety of ways to see if it is immediately related to a local player.
     * @param entity the entity to check (probably a player, client, or character entity)
     * @return true if any such check passes, otherwise false
     */
    private boolean relatesToLocalPlayer(EntityRef entity) {
        if (entity == null || entity.equals(EntityRef.NULL)) {
            logger.info("checkForLocalPlayer given a bad entity (null or NullEntityRef - can't relate that to a local player!");
            return false;
        }

        if (entity.equals(localPlayer.getClientEntity())) {
            logger.info("checkForLocalPlayer found a match to the localPlayer client entity! {}", entity);
            return true;
        }

        if (entity.equals(localPlayer.getCharacterEntity())) {
            logger.info("checkForLocalPlayer found a match to the localPlayer character entity! {}", entity);
            return true;
        }

        if (entity.equals(localPlayer.getClientInfoEntity())) {
            logger.info("checkForLocalPlayer found a match to the localPlayer client info entity! {}", entity);
            return true;
        }

        // In case we're in a scenario where localPlayer is unreliable this is an alternative way to check
        // This was needed in one case with headless + one client where an event triggered when localPlayer wasn't set right
        EntityRef networkSystemProvidedClientEntity = localPlayerSystem.getClientEntityViaNetworkSystem();
        if (entity.equals(networkSystemProvidedClientEntity)) {
            logger.info("checkForLocalPlayer found its entity to match the network system provided local client entity! {}", entity);
        }

        if (entity.hasComponent(CharacterComponent.class)) {
            EntityRef controller = entity.getComponent(CharacterComponent.class).controller;

            if (controller != null) {
                if (controller.equals(localPlayer.getClientEntity())) {
                    logger.info("checkForLocalPlayer found its entity to be a character whose controller matched the localPlayer client entity! {}", controller);
                    return true;
                }

                if (controller.equals(networkSystemProvidedClientEntity)) {
                    logger.info("checkForLocalPlayer found its entity to be a character controller matching the network system provided local client entity! {}", controller);
                    return true;
                }
            }
        }

        return false;
    }
}
