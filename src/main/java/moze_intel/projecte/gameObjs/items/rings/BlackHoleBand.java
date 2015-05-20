package moze_intel.projecte.gameObjs.items.rings;

import baubles.api.BaubleType;
import baubles.api.IBauble;
import com.google.common.collect.Lists;
import moze_intel.projecte.api.IAlchChestItem;
import moze_intel.projecte.api.IPedestalItem;
import moze_intel.projecte.gameObjs.entity.EntityLootBall;
import moze_intel.projecte.gameObjs.tiles.AlchChestTile;
import moze_intel.projecte.gameObjs.tiles.DMPedestalTile;
import moze_intel.projecte.utils.ItemHelper;
import moze_intel.projecte.utils.WorldHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.Optional;

import java.util.List;

@Optional.Interface(iface = "baubles.api.IBauble", modid = "Baubles")
public class BlackHoleBand extends RingToggle implements IAlchChestItem, IBauble, IPedestalItem
{
	public BlackHoleBand()
	{
		super("black_hole");
		this.setNoRepair();
	}
	
	@Override
	public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player)
	{
		if (!world.isRemote)
		{
			changeMode(player, stack);
		}
		
		return stack;
	}
	
	@Override
	public void onUpdate(ItemStack stack, World world, Entity entity, int par4, boolean par5) 
	{
		if (stack.getItemDamage() != 1 || !(entity instanceof EntityPlayer))
		{
			return;
		}
		
		EntityPlayer player = (EntityPlayer) entity;
		AxisAlignedBB bBox = player.getEntityBoundingBox().expand(7, 7, 7);
		List<EntityItem> itemList = world.getEntitiesWithinAABB(EntityItem.class, bBox);
		
		for (EntityItem item : itemList)
		{
			if (ItemHelper.hasSpace(player.inventory.mainInventory, item.getEntityItem()))
			{
				WorldHelper.gravitateEntityTowards(item, player.posX, player.posY, player.posZ);
			}
		}
		
		List<EntityLootBall> ballList = world.getEntitiesWithinAABB(EntityLootBall.class, bBox);
		
		for (EntityLootBall ball : ballList)
		{
			WorldHelper.gravitateEntityTowards(ball, player.posX, player.posY, player.posZ);
		}
	}

	@Override
	@Optional.Method(modid = "Baubles")
	public BaubleType getBaubleType(ItemStack itemstack)
	{
		return BaubleType.RING;
	}

	@Override
	@Optional.Method(modid = "Baubles")
	public void onWornTick(ItemStack stack, EntityLivingBase player) 
	{
		this.onUpdate(stack, player.worldObj, player, 0, false);
	}

	@Override
	@Optional.Method(modid = "Baubles")
	public void onEquipped(ItemStack itemstack, EntityLivingBase player) {}

	@Override
	@Optional.Method(modid = "Baubles")
	public void onUnequipped(ItemStack itemstack, EntityLivingBase player) {}

	@Override
	@Optional.Method(modid = "Baubles")
	public boolean canEquip(ItemStack itemstack, EntityLivingBase player) 
	{
		return true;
	}

	@Override
	@Optional.Method(modid = "Baubles")
	public boolean canUnequip(ItemStack itemstack, EntityLivingBase player) 
	{
		return true;
	}

	@Override
	public void updateInPedestal(World world, BlockPos pos)
	{
		DMPedestalTile tile = ((DMPedestalTile) world.getTileEntity(pos));
		if (tile != null)
		{
			List<EntityItem> list = world.getEntitiesWithinAABB(EntityItem.class, tile.getEffectBounds());
			for (EntityItem item : list)
			{
				WorldHelper.gravitateEntityTowards(item, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
				if (!world.isRemote && item.getDistanceSq(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) < 1.21 && !item.isDead)
				{
					suckDumpItem(item, tile);
				}
			}
		}
	}

	private void suckDumpItem(EntityItem item, DMPedestalTile tile)
	{
		List<TileEntity> list = WorldHelper.getAdjacentTileEntities(tile.getWorld(), tile);
		for (TileEntity tileEntity : list)
		{
			if (tileEntity instanceof IInventory)
			{
				IInventory inv = ((IInventory) tileEntity);
				ItemStack result = ItemHelper.pushStackInInv(inv, item.getEntityItem());
				if (result != null)
				{
					item.setEntityItemStack(result);
				}
				else
				{
					item.setDead();
					break;
				}
			}
		}
	}

	@Override
	public List<String> getPedestalDescription()
	{
		return Lists.newArrayList(
				EnumChatFormatting.BLUE + StatCollector.translateToLocal("pe.bhb.pedestal1"),
				EnumChatFormatting.BLUE + StatCollector.translateToLocal("pe.bhb.pedestal2")
		);
	}

	@Override
	public void updateInAlchChest(AlchChestTile tile, ItemStack stack)
	{
		if (stack.getItemDamage() == 1)
		{
			AxisAlignedBB aabb = new AxisAlignedBB(tile.xCoord - 5, tile.yCoord - 5, tile.zCoord - 5, tile.xCoord + 5, tile.yCoord + 5, tile.zCoord + 5);
			double centeredX = tile.xCoord + 0.5;
			double centeredY = tile.yCoord + 0.5;
			double centeredZ = tile.zCoord + 0.5;

			for (EntityItem e : (List<EntityItem>) tile.getWorld().getEntitiesWithinAABB(EntityItem.class, aabb))
			{
				WorldHelper.gravitateEntityTowards(e, centeredX, centeredY, centeredZ);
				if (!e.worldObj.isRemote && !e.isDead && e.getDistanceSq(centeredX, centeredY, centeredZ) < 1.21)
				{
					ItemStack result = ItemHelper.pushStackInInv(tile, e.getEntityItem());
					if (result != null)
					{
						e.setEntityItemStack(result);
					}
					else
					{
						e.setDead();
					}
				}
			}

			for (EntityLootBall e : (List<EntityLootBall>) tile.getWorld().getEntitiesWithinAABB(EntityLootBall.class, aabb))
			{
				WorldHelper.gravitateEntityTowards(e, centeredX, centeredY, centeredZ);
				if (!e.worldObj.isRemote && !e.isDead && e.getDistanceSq(centeredX, centeredY, centeredZ) < 1.21)
				{
					ItemHelper.pushLootBallInInv(tile, e);
				}
			}
		}
	}
}
