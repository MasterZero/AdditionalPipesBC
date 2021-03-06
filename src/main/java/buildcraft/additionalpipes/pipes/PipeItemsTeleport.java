/**
 * BuildCraft is open-source. It is distributed under the terms of the
 * BuildCraft Open Source License. It grants rights to read, modify, compile
 * or run the code. It does *NOT* grant the right to redistribute this software
 * or its modifications in any form, binary or source, except if expressively
 * granted by the copyright holder.
 */

package buildcraft.additionalpipes.pipes;

import java.util.LinkedList;
import java.util.List;

import net.minecraft.item.Item;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.Vec3;
import buildcraft.additionalpipes.api.PipeType;
import buildcraft.additionalpipes.utils.Log;
import buildcraft.core.lib.utils.Utils;
import buildcraft.transport.PipeTransportItems;
import buildcraft.transport.TravelingItem;
import buildcraft.transport.pipes.events.PipeEventItem;

public class PipeItemsTeleport extends PipeTeleport<PipeTransportItems> {
	private static final int ICON = 0;

	public PipeItemsTeleport(Item items) {
		super(new PipeTransportItems(), items, PipeType.ITEMS);
	}
	
	public void eventHandler(PipeEventItem.Entered event)
	{
		if(getWorld().isRemote) 
		{
			return;
		}
		
		if(event.item.hasExtraData())
		{
			if(event.item.getExtraData().hasKey("justTeleported"))
			{
				// this was sent to us by another teleport pipe
				event.item.getExtraData().removeTag("justTeleported");
				return;
			}
		}
		
		List<PipeItemsTeleport> connectedTeleportPipes = TeleportManager.instance.getConnectedPipes(this, false, true);
		
		// no teleport pipes connected, use default
		if(connectedTeleportPipes.size() <= 0 || (state & 0x1) == 0) {
			return;
		}

		// output to random pipe
		LinkedList<EnumFacing> outputOrientations = new LinkedList<EnumFacing>();
		PipeTeleport<?> otherPipe;
		
		int originalPipeNumber = rand.nextInt(connectedTeleportPipes.size());
		int currentPipeNumber = originalPipeNumber;
		
		boolean found = false;
		int numberOfTries = 0;
		
		// find a pipe with something connected to it
		// The logic for this is... pretty complicated, actually.
		do
		{
			++numberOfTries;
			otherPipe = connectedTeleportPipes.get(currentPipeNumber);
			
			for(EnumFacing o : EnumFacing.values())
			{
				if(otherPipe.outputOpen(o))
				{
					outputOrientations.add(o);
				}
			}
			
			// no outputs found, try again
			if(outputOrientations.size() <= 0) 
			{
				++currentPipeNumber;
				
				//loop back to the start
				if(currentPipeNumber >= connectedTeleportPipes.size())
				{
					currentPipeNumber = 0;
				}
			}
			else
			{
				found = true;
			}
		}
		while(numberOfTries < connectedTeleportPipes.size() && !found);

		//couldn't find any, so give up
		if(!found)
		{
			return;
		}
		
		//can no longer set position of TravelingItems as of BC 7.2, so we have to make a new one
		EnumFacing fromOrientation = otherPipe.getOpenOrientation().getOpposite();
		
		Vec3 travelingItemPos = Utils.convertMiddle(otherPipe.container.getPos());//.add(Utils.convert(fromOrientation, -0.5));;		
		TravelingItem newItem = TravelingItem.make(travelingItemPos, event.item.getItemStack());
		
		// add the NBT tag to the item to let the receiving pipe know not to send the item back
		newItem.getExtraData().setInteger("justTeleported", getFrequency());
		
		// actually inject the item
		((PipeTransportItems) otherPipe.transport).injectItem(newItem, fromOrientation);

		Log.debug(event.item + " from " + getPosition() + " to " + otherPipe.getPosition() + " insertion point: " + travelingItemPos.toString());
		event.cancelled = true;
	}

	@Override
	public int getIconIndex(EnumFacing direction) {
		return ICON;
	}

}
