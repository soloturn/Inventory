// Copyright 2021 The Terasology Foundation
// SPDX-License-Identifier: Apache-2.0
package org.terasology.module.inventory.systems;

import org.terasology.engine.entitySystem.entity.EntityRef;
import org.terasology.engine.entitySystem.systems.BaseComponentSystem;
import org.terasology.engine.entitySystem.systems.RegisterMode;
import org.terasology.engine.entitySystem.systems.RegisterSystem;
import org.terasology.engine.logic.players.LocalPlayer;
import org.terasology.engine.registry.In;
import org.terasology.engine.registry.Share;
import org.terasology.gestalt.entitysystem.event.ReceiveEvent;
import org.terasology.module.inventory.components.InventoryComponent;
import org.terasology.module.inventory.events.AbstractMoveItemRequest;
import org.terasology.module.inventory.events.InventoryChangeAcknowledgedRequest;
import org.terasology.module.inventory.events.MoveItemAction;
import org.terasology.module.inventory.events.MoveItemAmountRequest;
import org.terasology.module.inventory.events.MoveItemRequest;
import org.terasology.module.inventory.events.MoveItemToSlotsRequest;
import org.terasology.module.inventory.events.SwitchItemAction;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RegisterSystem(RegisterMode.REMOTE_CLIENT)
@Share(InventoryManager.class)
public class InventoryClientSystem extends BaseComponentSystem implements InventoryManager {

    @In
    private LocalPlayer localPlayer;

    private final Map<Integer, AbstractMoveItemRequest> pendingMoves = new LinkedHashMap<>();

    private int changeId;

    // We support only these two events on the client, as these are the events used by inventory UI

    @ReceiveEvent(components = InventoryComponent.class)
    public void switchItemRequest(SwitchItemAction event, EntityRef entity) {
        switchItem(entity, event.getInstigator(), event.getSlotFrom(), event.getTo(), event.getSlotTo());
    }

    @ReceiveEvent(components = InventoryComponent.class)
    public void moveItemRequest(MoveItemAction event, EntityRef entity) {
        moveItem(entity, event.getInstigator(), event.getSlotFrom(), event.getTo(), event.getSlotTo(), event.getCount());
    }

    @ReceiveEvent(components = InventoryComponent.class)
    public void inventoryChangeAcknowledge(InventoryChangeAcknowledgedRequest event, EntityRef entity) {
        //TODO: This does not ever get triggered because the event is sent to the client,  not the character.
        //      If it did get triggered,  it causes a mess of question mark items.
        AbstractMoveItemRequest removedRequest = pendingMoves.remove(event.getChangeId());
        if (removedRequest != null) {
            destroyClientTempEntities(removedRequest);
        }

        recalculatePredictedState();
    }

    private void destroyClientTempEntities(AbstractMoveItemRequest removedRequest) {
        for (EntityRef tempEntity : removedRequest.getClientSideTempEntities()) {
            if (tempEntity.exists()) {
                tempEntity.destroy();
            }
        }
    }

    private void recalculatePredictedState() {
        for (AbstractMoveItemRequest request : pendingMoves.values()) {
            // For each remaining pending request, we need to destroy all temp entities that request previously created,
            // then redo the change requested and store new temp entities in the request to be destroyed once that
            // pending request is acknowledged by the server
            if (request instanceof MoveItemRequest) {
                MoveItemRequest r = (MoveItemRequest) request;
                destroyClientTempEntities(r);

                Collection<EntityRef> newClientTempEntities = new HashSet<>();
                moveItemFillClientTempEntities(request.getFromInventory(), r.getInstigator(), r.getFromSlot(), r.getToInventory(),
                        r.getToSlot(), newClientTempEntities);
                r.setClientSideTempEntities(newClientTempEntities);
            } else if (request instanceof MoveItemAmountRequest) {
                MoveItemAmountRequest r = (MoveItemAmountRequest) request;
                destroyClientTempEntities(r);

                Collection<EntityRef> newClientTempEntities = new HashSet<>();
                moveItemAmountFillClientTempEntities(r.getFromInventory(), r.getInstigator(), r.getFromSlot(), r.getToInventory(),
                        r.getToSlot(), r.getAmount(), newClientTempEntities);
                r.setClientSideTempEntities(newClientTempEntities);
            } else if (request instanceof MoveItemToSlotsRequest) {
                MoveItemToSlotsRequest r = (MoveItemToSlotsRequest) request;
                destroyClientTempEntities(r);

                Collection<EntityRef> newClientTempEntities = new HashSet<>();
                moveItemToSlotsFillClientTempEntities(r.getInstigator(), r.getFromInventory(), r.getFromSlot(), r.getToInventory(),
                        r.getToSlots(), newClientTempEntities);
                r.setClientSideTempEntities(newClientTempEntities);
            }
        }
    }

    @Override
    public boolean canStackTogether(EntityRef itemA, EntityRef itemB) {
        return InventoryUtils.canStackInto(itemA, itemB);
    }

    @Override
    public int getStackSize(EntityRef item) {
        return InventoryUtils.getStackCount(item);
    }

    @Override
    public EntityRef getItemInSlot(EntityRef inventoryEntity, int slot) {
        return InventoryUtils.getItemAt(inventoryEntity, slot);
    }

    @Override
    public int findSlotWithItem(EntityRef inventoryEntity, EntityRef item) {
        return InventoryUtils.getSlotWithItem(inventoryEntity, item);
    }

    @Override
    public int getNumSlots(EntityRef inventoryEntity) {
        return InventoryUtils.getSlotCount(inventoryEntity);
    }

    @Override
    public boolean giveItem(EntityRef inventory, EntityRef instigator, EntityRef item) {
        throw new UnsupportedOperationException("This operation cannot be invoked on the client");
    }

    @Override
    public boolean giveItem(EntityRef inventory, EntityRef instigator, EntityRef item, int slot) {
        throw new UnsupportedOperationException("This operation cannot be invoked on the client");
    }

    @Override
    public boolean giveItem(EntityRef inventory, EntityRef instigator, EntityRef item, List<Integer> slots) {
        throw new UnsupportedOperationException("This operation cannot be invoked on the client");
    }

    @Override
    public EntityRef removeItem(EntityRef inventory, EntityRef instigator, EntityRef item, boolean destroyRemoved) {
        throw new UnsupportedOperationException("This operation cannot be invoked on the client");
    }

    @Override
    public EntityRef removeItem(EntityRef inventory, EntityRef instigator, EntityRef item, boolean destroyRemoved, int count) {
        throw new UnsupportedOperationException("This operation cannot be invoked on the client");
    }

    @Override
    public EntityRef removeItem(EntityRef inventory, EntityRef instigator, List<EntityRef> items, boolean destroyRemoved) {
        throw new UnsupportedOperationException("This operation cannot be invoked on the client");
    }

    @Override
    public EntityRef removeItem(EntityRef inventory, EntityRef instigator, List<EntityRef> items, boolean destroyRemoved, int count) {
        throw new UnsupportedOperationException("This operation cannot be invoked on the client");
    }

    @Override
    public EntityRef removeItem(EntityRef inventory, EntityRef instigator, int slotNo, boolean destroyRemoved, int count) {
        throw new UnsupportedOperationException("This operation cannot be invoked on the client");
    }

    @Override
    public boolean moveItem(EntityRef fromInventory, EntityRef instigator, int slotFrom, EntityRef toInventory, int slotTo, int count) {
        Collection<EntityRef> clientTempEntities = new HashSet<>();
        if (moveItemAmountFillClientTempEntities(fromInventory, instigator, slotFrom, toInventory, slotTo, count, clientTempEntities)) {
            return false;
        }

        MoveItemAmountRequest request = new MoveItemAmountRequest(instigator, fromInventory,
                slotFrom, toInventory, slotTo, count, changeId++, clientTempEntities);
        pendingMoves.put(request.getChangeId(), request);
        localPlayer.getClientEntity().send(request);

        return true;
    }

    private boolean moveItemAmountFillClientTempEntities(EntityRef fromInventory, EntityRef instigator, int slotFrom,
            EntityRef toInventory, int slotTo, int count, Collection<EntityRef> clientTempEntities) {
        EntityRef itemAtBefore = InventoryUtils.getItemAt(toInventory, slotTo);
        boolean itemExisted = itemAtBefore.exists();
        if (!InventoryUtils.moveItemAmount(instigator, fromInventory, slotFrom, toInventory, slotTo, count)) {
            return true;
        }

        if (!itemExisted) {
            clientTempEntities.add(InventoryUtils.getItemAt(toInventory, slotTo));
        }
        return false;
    }


    @Override
    public boolean moveItemToSlots(EntityRef instigator, EntityRef fromInventory, int slotFrom, EntityRef toInventory, List<Integer> toSlots) {
        Collection<EntityRef> clientTempEntities = new HashSet<>();
        if (moveItemToSlotsFillClientTempEntities(instigator, fromInventory, slotFrom, toInventory, toSlots, clientTempEntities)) {
            return false;
        }

        MoveItemToSlotsRequest request = new MoveItemToSlotsRequest(instigator, fromInventory,
                slotFrom, toInventory, toSlots, changeId++, clientTempEntities);
        pendingMoves.put(request.getChangeId(), request);
        localPlayer.getClientEntity().send(request);

        return true;
    }

    private boolean moveItemToSlotsFillClientTempEntities(EntityRef instigator, EntityRef fromInventory, int slotFrom,
            EntityRef toInventory, List<Integer> toSlots, Collection<EntityRef> clientTempEntities) {
        Set<Integer> emptySlotsBefore = new HashSet<>();
        for (Integer toSlot : toSlots) {
            if (!InventoryUtils.getItemAt(toInventory, toSlot).exists()) {
                emptySlotsBefore.add(toSlot);
            }
        }

        if (!InventoryUtils.moveItemToSlots(instigator, fromInventory, slotFrom, toInventory, toSlots)) {
            return true;
        }

        for (Integer slot : emptySlotsBefore) {
            EntityRef itemAt = InventoryUtils.getItemAt(toInventory, slot);
            if (itemAt.exists()) {
                clientTempEntities.add(itemAt);
            }
        }
        return false;
    }

    @Override
    public boolean switchItem(EntityRef fromInventory, EntityRef instigator, int slotFrom, EntityRef toInventory, int slotTo) {
        Collection<EntityRef> clientTempEntities = new HashSet<>();
        if (moveItemFillClientTempEntities(fromInventory, instigator, slotFrom, toInventory, slotTo, clientTempEntities)) {
            return false;
        }

        MoveItemRequest request = new MoveItemRequest(instigator, fromInventory, slotFrom, toInventory, slotTo, changeId++, clientTempEntities);
        pendingMoves.put(request.getChangeId(), request);
        localPlayer.getClientEntity().send(request);

        return true;
    }

    private boolean moveItemFillClientTempEntities(EntityRef fromInventory, EntityRef instigator, int slotFrom,
            EntityRef toInventory, int slotTo, Collection<EntityRef> clientTempEntities) {
        boolean slotFromEmpty = !InventoryUtils.getItemAt(fromInventory, slotFrom).exists();
        boolean slotToEmpty = !InventoryUtils.getItemAt(toInventory, slotTo).exists();
        if (!InventoryUtils.moveItem(instigator, fromInventory, slotFrom, toInventory, slotTo)) {
            return true;
        }

        if (slotFromEmpty) {
            EntityRef itemAt = InventoryUtils.getItemAt(fromInventory, slotFrom);
            if (itemAt.exists()) {
                clientTempEntities.add(itemAt);
            }
        }
        if (slotToEmpty) {
            EntityRef itemAt = InventoryUtils.getItemAt(toInventory, slotTo);
            if (itemAt.exists()) {
                clientTempEntities.add(itemAt);
            }
        }
        return false;
    }
}
