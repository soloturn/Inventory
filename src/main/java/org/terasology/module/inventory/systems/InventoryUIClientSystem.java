// Copyright 2021 The Terasology Foundation
// SPDX-License-Identifier: Apache-2.0
package org.terasology.module.inventory.systems;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.terasology.engine.entitySystem.entity.EntityRef;
import org.terasology.engine.entitySystem.event.EventPriority;
import org.terasology.engine.entitySystem.event.Priority;
import org.terasology.engine.entitySystem.systems.BaseComponentSystem;
import org.terasology.engine.entitySystem.systems.RegisterMode;
import org.terasology.engine.entitySystem.systems.RegisterSystem;
import org.terasology.engine.logic.characters.CharacterComponent;
import org.terasology.engine.logic.characters.interactions.InteractionUtil;
import org.terasology.engine.logic.players.LocalPlayer;
import org.terasology.engine.network.ClientComponent;
import org.terasology.engine.registry.In;
import org.terasology.engine.rendering.nui.NUIManager;
import org.terasology.gestalt.assets.ResourceUrn;
import org.terasology.gestalt.entitysystem.event.ReceiveEvent;
import org.terasology.input.ButtonState;
import org.terasology.module.inventory.components.InventoryComponent;
import org.terasology.module.inventory.input.InventoryButton;
import org.terasology.nui.ControlWidget;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@RegisterSystem(RegisterMode.CLIENT)
public class InventoryUIClientSystem extends BaseComponentSystem {

    private static final Logger logger = LoggerFactory.getLogger(InventoryUIClientSystem.class);

    EntityRef movingItemItem = EntityRef.NULL;

    int movingItemCount = 0;

    @In
    private NUIManager nuiManager;

    @In
    private InventoryManager inventoryManager;

    @In
    private LocalPlayer localPlayer;

    @Override
    public void initialise() {
        nuiManager.getHUD().addHUDElement("inventoryHud");
        nuiManager.addOverlay("Inventory:transferItemCursor", ControlWidget.class);
    }

    @ReceiveEvent(components = ClientComponent.class)
    public void onToggleInventory(InventoryButton event, EntityRef entity) {
        if (event.getState() == ButtonState.DOWN) {
            nuiManager.toggleScreen("Inventory:inventoryScreen");
            event.consume();
        }
    }

    /*
     * At the activation of the inventory the current dialog needs to be closed instantly.
     *
     * The close of the dialog triggers {@link #onScreenLayerClosed} which resets the
     * interactionTarget.
     */
    @Priority(EventPriority.PRIORITY_HIGH)
    @ReceiveEvent(components = ClientComponent.class)
    public void onToggleInventory(InventoryButton event, EntityRef entity, ClientComponent clientComponent) {
        if (event.getState() != ButtonState.DOWN) {
            return;
        }

        EntityRef character = clientComponent.character;
        ResourceUrn activeInteractionScreenUri = InteractionUtil.getActiveInteractionScreenUri(character);
        if (activeInteractionScreenUri != null) {
            InteractionUtil.cancelInteractionAsClient(character);
            // do not consume the event, so that the inventory will still open
        }
    }

    private EntityRef getTransferEntity() {
        return localPlayer.getCharacterEntity().getComponent(CharacterComponent.class).movingItem;
    }

    @Override
    public void preAutoSave() {
        /*
          The code below was originally taken from moveItemSmartly() in
          InventoryCell.class and slightly modified to work here.

          The way items are being moved to and from the hotbar is really
          similar to what was needed here to take them out of the transfer
          slot and sort them into the inventory.
        */
        EntityRef playerEntity = localPlayer.getCharacterEntity();
        EntityRef movingItemSlot = playerEntity.getComponent(CharacterComponent.class).movingItem;

        movingItemItem = inventoryManager.getItemInSlot(movingItemSlot, 0);

        movingItemCount = inventoryManager.getStackSize(movingItemItem);

        EntityRef fromEntity = movingItemSlot;
        int fromSlot = 0;

        InventoryComponent playerInventory = playerEntity.getComponent(InventoryComponent.class);

        if (movingItemItem != EntityRef.NULL) {

            if (playerInventory == null) {
                return;
            }
            CharacterComponent characterComponent = playerEntity.getComponent(CharacterComponent.class);
            if (characterComponent == null) {
                logger.error("Character entity of player had no character component");
                return;
            }
            int totalSlotCount = playerInventory.itemSlots.size();

            EntityRef interactionTarget = characterComponent.predictedInteractionTarget;
            InventoryComponent interactionTargetInventory = interactionTarget.getComponent(InventoryComponent.class);

            EntityRef targetEntity;
            List<Integer> toSlots = new ArrayList<>(totalSlotCount);
            if (fromEntity.equals(playerEntity) && interactionTarget.exists() && interactionTargetInventory != null) {
                targetEntity = interactionTarget;
                toSlots = IntStream.range(0, interactionTargetInventory.itemSlots.size()).boxed().collect(Collectors.toList());
            } else {
                targetEntity = playerEntity;
                toSlots = IntStream.range(0, totalSlotCount).boxed().collect(Collectors.toList());
            }

            inventoryManager.moveItemToSlots(getTransferEntity(), fromEntity, fromSlot, targetEntity, toSlots);
        }
    }

    @Override
    public void postAutoSave() {
        if (movingItemItem != EntityRef.NULL) {
            EntityRef playerEntity = localPlayer.getCharacterEntity();
            EntityRef movingItem = playerEntity.getComponent(CharacterComponent.class).movingItem;

            EntityRef targetEntity = movingItem;
            EntityRef fromEntity = playerEntity;

            int currentSlot = playerEntity.getComponent(InventoryComponent.class).itemSlots.size() - 1;

            while (currentSlot >= 0 && movingItemCount > 0) {

                EntityRef currentItem = inventoryManager.getItemInSlot(playerEntity, currentSlot);
                int currentItemCount = inventoryManager.getStackSize(currentItem);
                boolean correctItem = (currentItem == movingItemItem);

                if (correctItem) {
                    int count = Math.min(movingItemCount, currentItemCount);
                    inventoryManager.moveItem(fromEntity, getTransferEntity(), currentSlot, targetEntity, 0, count);
                    movingItemCount -= count;
                }

                currentSlot--;
            }
        }

        movingItemItem = EntityRef.NULL;
    }
}
