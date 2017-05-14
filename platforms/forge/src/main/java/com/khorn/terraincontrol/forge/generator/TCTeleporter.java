package com.khorn.terraincontrol.forge.generator;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Random;

import com.khorn.terraincontrol.LocalMaterialData;
import com.khorn.terraincontrol.LocalWorld;
import com.khorn.terraincontrol.TerrainControl;
import com.khorn.terraincontrol.forge.ForgeEngine;
import com.khorn.terraincontrol.forge.ForgeMaterialData;
import com.khorn.terraincontrol.forge.ForgeWorld;

import net.minecraft.block.BlockPortal;
import net.minecraft.block.state.IBlockState;
import net.minecraft.block.state.pattern.BlockPattern;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.play.server.SPacketEffect;
import net.minecraft.network.play.server.SPacketEntityEffect;
import net.minecraft.network.play.server.SPacketPlayerAbilities;
import net.minecraft.network.play.server.SPacketRespawn;
import net.minecraft.potion.PotionEffect;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.PlayerList;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.DimensionType;
import net.minecraft.world.Teleporter;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.fml.common.FMLCommonHandler;

public class TCTeleporter
{
	// Items

    public static Entity changeDimension(int dimensionIn, Entity _this)
    {
        if (!_this.worldObj.isRemote && !_this.isDead)
        {
            if (!net.minecraftforge.common.ForgeHooks.onTravelToDimension(_this, dimensionIn)) return null;
            _this.worldObj.theProfiler.startSection("changeDimension");
            MinecraftServer minecraftserver = _this.getServer();
            int i = _this.dimension;
            WorldServer worldserver = minecraftserver.worldServerForDimension(i);
            WorldServer worldserver1 = minecraftserver.worldServerForDimension(dimensionIn);
            _this.dimension = dimensionIn;

            if (i == 1 && dimensionIn == 1)
            {
                worldserver1 = minecraftserver.worldServerForDimension(0);
                _this.dimension = 0;
            }

            _this.worldObj.removeEntity(_this);
            _this.isDead = false;
            _this.worldObj.theProfiler.startSection("reposition");

            worldserver.updateEntityWithOptionalForce(_this, false);
            _this.worldObj.theProfiler.endStartSection("reloading");
            Entity entity = EntityList.createEntityByName(EntityList.getEntityString(_this), worldserver1);

            if (entity != null)
            {
                copyDataFromOld(_this, entity);

                boolean flag = entity.forceSpawn;
                entity.forceSpawn = true;
                                
                LocalWorld forgeWorld = ((ForgeEngine)TerrainControl.getEngine()).getWorld(DimensionManager.getWorld(i));
                ArrayList<LocalMaterialData> portalMaterials = forgeWorld.getConfigs().getWorldConfig().DimensionPortalMaterials;
                
            	placeInPortal((ForgeMaterialData)portalMaterials.get(0), worldserver1, entity, entity.rotationYaw, worldserver1.getDefaultTeleporter());
                
                worldserver1.spawnEntityInWorld(entity);
                
                entity.forceSpawn = flag;
                worldserver1.updateEntityWithOptionalForce(entity, false);
            }

            _this.isDead = true;
            _this.worldObj.theProfiler.endSection();
            worldserver.resetUpdateEntityTick();
            worldserver1.resetUpdateEntityTick();
            _this.worldObj.theProfiler.endSection();           
            
            return entity;
        } else {
            return null;
        }
    }	
    
    private static void copyDataFromOld(Entity entityIn, Entity _this)
    {
        NBTTagCompound nbttagcompound = entityIn.writeToNBT(new NBTTagCompound());
        nbttagcompound.removeTag("Dimension");
        _this.readFromNBT(nbttagcompound);
        _this.timeUntilPortal = entityIn.timeUntilPortal;
    }
    
	// Players
	
	public static Entity changeDimension(int dimensionIn, EntityPlayerMP _this)
    {		
    	DimensionType dimType = DimensionManager.getProviderType(dimensionIn);
    	if(dimType.getName().equals("DIM-Cartographer"))
    	{
    		ForgeWorld cartographerWorld = (ForgeWorld)TerrainControl.getWorld("DIM-Cartographer");

    		if(cartographerWorld == null)
    		{
    			DimensionManager.initDimension(Cartographer.CartographerDimension);
    			cartographerWorld = (ForgeWorld)TerrainControl.getWorld("DIM-Cartographer");
    		}
    		if(cartographerWorld == null)
    		{
    			throw new RuntimeException("Whatever it is you're trying to do, we didn't write any code for it (sorry). Please contact Team OTG about this crash.");
    		}

    		BlockPos cartographerSpawnPoint = cartographerWorld.getSpawnPoint();
    		_this.setLocationAndAngles(cartographerSpawnPoint.getX(), 125, cartographerSpawnPoint.getZ(), 0, 0);
    	}

        if (!net.minecraftforge.common.ForgeHooks.onTravelToDimension(_this, dimensionIn)) return _this;

		if(((ForgeEngine)TerrainControl.getEngine()).getCartographerEnabled() && dimensionIn == Cartographer.CartographerDimension)
		{
			_this.capabilities.allowEdit = false;
			_this.capabilities.allowFlying = true;
			_this.sendPlayerAbilities();
		} else {
			_this.capabilities.allowEdit = true;
			_this.capabilities.allowFlying = _this.capabilities.isCreativeMode;
			_this.sendPlayerAbilities();
		}
        
        changePlayerDimension(_this, dimensionIn, _this.mcServer.getPlayerList());
        _this.connection.sendPacket(new SPacketEffect(1032, BlockPos.ORIGIN, 0, false));
        return _this;
    }
    
    public static void changePlayerDimension(EntityPlayerMP player, int dimensionIn, PlayerList _this)
    {    	    	
        transferPlayerToDimension(player, dimensionIn, _this.getServerInstance().worldServerForDimension(dimensionIn).getDefaultTeleporter(), _this);
    }

    public static void transferPlayerToDimension(EntityPlayerMP player, int dimensionIn, net.minecraft.world.Teleporter teleporter, PlayerList _this)
    {
        int i = player.dimension;
        WorldServer worldserver = _this.getServerInstance().worldServerForDimension(player.dimension);
        player.dimension = dimensionIn;
        WorldServer worldserver1 = _this.getServerInstance().worldServerForDimension(player.dimension);
        player.connection.sendPacket(new SPacketRespawn(player.dimension, worldserver1.getDifficulty(), worldserver1.getWorldInfo().getTerrainType(), player.interactionManager.getGameType()));
        _this.updatePermissionLevel(player);
        worldserver.removeEntityDangerously(player);
        player.isDead = false;
        transferEntityToWorld(player, i, worldserver, worldserver1, teleporter);
        _this.preparePlayer(player, worldserver);
        player.connection.setPlayerLocation(player.posX, player.posY, player.posZ, player.rotationYaw, player.rotationPitch);
        player.interactionManager.setWorld(worldserver1);
        player.connection.sendPacket(new SPacketPlayerAbilities(player.capabilities));
        _this.updateTimeAndWeatherForPlayer(player, worldserver1);
        _this.syncPlayerInventory(player);

        for (PotionEffect potioneffect : player.getActivePotionEffects())
        {
            player.connection.sendPacket(new SPacketEntityEffect(player.getEntityId(), potioneffect));
        }
        net.minecraftforge.fml.common.FMLCommonHandler.instance().firePlayerChangedDimensionEvent(player, i, dimensionIn);
    }
    
    public static void transferEntityToWorld(Entity entityIn, int lastDimension, WorldServer oldWorldIn, WorldServer toWorldIn, net.minecraft.world.Teleporter teleporter)
    {
        net.minecraft.world.WorldProvider pOld = oldWorldIn.provider;
        net.minecraft.world.WorldProvider pNew = toWorldIn.provider;
        double moveFactor = pOld.getMovementFactor() / pNew.getMovementFactor();
        double d0 = entityIn.posX * moveFactor;
        double d1 = entityIn.posZ * moveFactor;
        float f = entityIn.rotationYaw;
        oldWorldIn.theProfiler.startSection("moving");

        oldWorldIn.theProfiler.endSection();

        if (lastDimension != 1)
        {
            oldWorldIn.theProfiler.startSection("placing");
            d0 = (double)MathHelper.clamp_int((int)d0, -29999872, 29999872);
            d1 = (double)MathHelper.clamp_int((int)d1, -29999872, 29999872);

            if (entityIn.isEntityAlive())
            {
                entityIn.setLocationAndAngles(d0, entityIn.posY, d1, entityIn.rotationYaw, entityIn.rotationPitch);
                
                if(!((ForgeEngine)TerrainControl.getEngine()).getCartographerEnabled() || (entityIn.dimension != Cartographer.CartographerDimension && lastDimension != Cartographer.CartographerDimension))
                {
	                LocalWorld forgeWorld = ((ForgeEngine)TerrainControl.getEngine()).getWorld(DimensionManager.getWorld(lastDimension));	                
	                ArrayList<LocalMaterialData> portalMaterials = forgeWorld.getConfigs().getWorldConfig().DimensionPortalMaterials;
	                
                	placeInPortal((ForgeMaterialData)portalMaterials.get(0), toWorldIn, entityIn, f, teleporter);
                }
                
                toWorldIn.spawnEntityInWorld(entityIn);
                toWorldIn.updateEntityWithOptionalForce(entityIn, false);
            }

            oldWorldIn.theProfiler.endSection();
        }

        entityIn.setWorld(toWorldIn);
    }
    
	private static Long2ObjectMap<Teleporter.PortalPosition> getPortals(int dimensionId)
	{
		Long2ObjectMap<Teleporter.PortalPosition> destinationCoordinateCache = null;

		MinecraftServer mcServer = FMLCommonHandler.instance().getMinecraftServerInstance();
    	WorldServer worldserver1 = mcServer.worldServerForDimension(dimensionId);
		try {
			Field[] fields = worldserver1.getDefaultTeleporter().getClass().getDeclaredFields();
			for(Field field : fields)
			{
				Class<?> fieldClass = field.getType();
				if(fieldClass.equals(Long2ObjectMap.class))
				{
					field.setAccessible(true);
					destinationCoordinateCache = (Long2ObjectMap) field.get(worldserver1.getDefaultTeleporter());
			        break;
				}
			}
		} catch (SecurityException e) {
			e.printStackTrace();
		} catch (IllegalArgumentException e) {
			e.printStackTrace();
		} catch (IllegalAccessException e) {
			e.printStackTrace();
		}

		return destinationCoordinateCache;
	}
    
    public static boolean placeInExistingPortal(WorldServer destinationWorld, Entity entityIn, float rotationYaw, Teleporter _this)
    {
        double d0 = -1.0D;
        int j = MathHelper.floor_double(entityIn.posX);
        int k = MathHelper.floor_double(entityIn.posZ);
        boolean flag = true;
        BlockPos blockpos = BlockPos.ORIGIN;
        long l = ChunkPos.asLong(j, k);

        Long2ObjectMap<Teleporter.PortalPosition> destinationCoordinateCache = getPortals(destinationWorld.provider.getDimension());
        
        if (destinationCoordinateCache.containsKey(l))
        {
            Teleporter.PortalPosition teleporter$portalposition = (Teleporter.PortalPosition)destinationCoordinateCache.get(l);
            d0 = 0.0D;
            blockpos = teleporter$portalposition;
            teleporter$portalposition.lastUpdateTime = destinationWorld.getTotalWorldTime();
            flag = false;
        } else {
            BlockPos blockpos3 = new BlockPos(entityIn);

            for (int i1 = -128; i1 <= 128; ++i1)
            {
                BlockPos blockpos2;

                for (int j1 = -128; j1 <= 128; ++j1)
                {
                    for (BlockPos blockpos1 = blockpos3.add(i1, destinationWorld.getActualHeight() - 1 - blockpos3.getY(), j1); blockpos1.getY() >= 0; blockpos1 = blockpos2)
                    {
                        blockpos2 = blockpos1.down();

                        if (destinationWorld.getBlockState(blockpos1).getBlock() == Blocks.PORTAL)
                        {
                            for (blockpos2 = blockpos1.down(); destinationWorld.getBlockState(blockpos2).getBlock() == Blocks.PORTAL; blockpos2 = blockpos2.down())
                            {
                                blockpos1 = blockpos2;
                            }

                            double d1 = blockpos1.distanceSq(blockpos3);

                            if (d0 < 0.0D || d1 < d0)
                            {
                                d0 = d1;
                                blockpos = blockpos1;
                            }
                        }
                    }
                }
            }
        }

        if (d0 >= 0.0D)
        {
            if (flag)
            {
            	destinationCoordinateCache.put(l, _this.new PortalPosition(blockpos, destinationWorld.getTotalWorldTime()));
            }

            double d5 = (double)blockpos.getX() + 0.5D;
            double d7 = (double)blockpos.getZ() + 0.5D;
            BlockPattern.PatternHelper blockpattern$patternhelper = Blocks.PORTAL.createPatternHelper(destinationWorld, blockpos);
            boolean flag1 = blockpattern$patternhelper.getForwards().rotateY().getAxisDirection() == EnumFacing.AxisDirection.NEGATIVE;
            double d2 = blockpattern$patternhelper.getForwards().getAxis() == EnumFacing.Axis.X ? (double)blockpattern$patternhelper.getFrontTopLeft().getZ() : (double)blockpattern$patternhelper.getFrontTopLeft().getX();
            
            double xCoord = entityIn.getLastPortalVec() == null ? 0.0 : entityIn.getLastPortalVec().xCoord;
            double yCoord = entityIn.getLastPortalVec() == null ? 2.0 : entityIn.getLastPortalVec().yCoord;
            double d6 = (double)(blockpattern$patternhelper.getFrontTopLeft().getY() + 1) - yCoord * (double)blockpattern$patternhelper.getHeight();

            if (flag1)
            {
                ++d2;
            }

            if (blockpattern$patternhelper.getForwards().getAxis() == EnumFacing.Axis.X)
            {
                d7 = d2 + (1.0D - xCoord) * (double)blockpattern$patternhelper.getWidth() * (double)blockpattern$patternhelper.getForwards().rotateY().getAxisDirection().getOffset();
            } else {
                d5 = d2 + (1.0D - xCoord) * (double)blockpattern$patternhelper.getWidth() * (double)blockpattern$patternhelper.getForwards().rotateY().getAxisDirection().getOffset();
            }
            
            entityIn.motionX = 0.0;
            entityIn.motionZ = 0.0;
            entityIn.rotationYaw = 0;

            if (entityIn instanceof EntityPlayerMP)
            {
            	// Find a suitable spawn location next to the portal
            	// Make it so that all positions near the portal are checked once but the spawn point is not always the same.
            	// In this case the xyz scan direction is random so that should create 2x2x2 = 8 different possible scan orders.
            	int radius = 2;
            	boolean invertX = Math.random() > 0.5;
            	boolean invertY = Math.random() > 0.5;
            	boolean invertZ = Math.random() > 0.5;
            	for(int x = -radius; x <= radius; x++)
            	{
            		int randomX = invertX ? -x : x;
            		for(int y = -radius; y <= radius; y++)
            		{
            			int randomY = invertY ? -y : y;
            			for(int z = -radius; z <= radius; z++)
            			{
            				int randomZ = invertZ ? -z : z;
            				
	            			BlockPos blockPos1 = new BlockPos(d5 + randomX, d6 + randomY, d7 + randomZ);
	            			BlockPos blockPos2 = new BlockPos(d5 + randomX, d6 + randomY + 1, d7 + randomZ);
	            			BlockPos blockPos3 = new BlockPos(d5 + randomX, d6 + randomY + 2, d7 + randomZ);
	            			IBlockState blockState1 = destinationWorld.getBlockState(blockPos1);
	            			IBlockState blockState2 = destinationWorld.getBlockState(blockPos2);
	            			IBlockState blockState3 = destinationWorld.getBlockState(blockPos3);

	            			if(
            					blockState1.getMaterial().blocksMovement() &&
            					!blockState2.getMaterial().blocksMovement() &&
            					!blockState3.getMaterial().blocksMovement() &&
            					!blockState2.getMaterial().isLiquid() &&
            					!blockState3.getMaterial().isLiquid() &&
            					blockState2.getBlock() != Blocks.FIRE &&
            					blockState3.getBlock() != Blocks.FIRE &&
            					blockState2.getBlock() != Blocks.PORTAL &&
            					blockState3.getBlock() != Blocks.PORTAL
        					)
	            			{
		            			((EntityPlayerMP)entityIn).connection.setPlayerLocation(blockPos2.getX() + 0.5, blockPos2.getY(), blockPos2.getZ() + 0.5, entityIn.rotationYaw, entityIn.rotationPitch);
		            			return true;
		            		}
            			}
            		}
            	}
            	// Could not find a suitable spawn location, have to spawn player inside portal so destroy portal so that the player doesn't get teleported back and forth 
            	destinationWorld.notifyNeighborsOfStateChange(new BlockPos(d5, d6 + 1, d7), destinationWorld.getBlockState(new BlockPos(d5, d6 + 1, d7)).getBlock());
                ((EntityPlayerMP)entityIn).connection.setPlayerLocation(d5, d6 + 1, d7, entityIn.rotationYaw, entityIn.rotationPitch);
            } else {      
            	// Find a suitable spawn location next to the portal
            	// Make it so that all positions near the portal are checked once but the spawn point is not always the same.
            	// In this case the xyz scan direction is random so that should create 2x2x2 = 8 different possible scan orders.
            	int radius = 2;
            	boolean invertX = Math.random() > 0.5;
            	boolean invertY = Math.random() > 0.5;
            	boolean invertZ = Math.random() > 0.5;
            	for(int x = -radius; x <= radius; x++)
            	{
            		int randomX = invertX ? -x : x;
            		for(int y = -radius; y <= radius; y++)
            		{
            			int randomY = invertY ? -y : y;
            			for(int z = -radius; z <= radius; z++)
            			{
            				int randomZ = invertZ ? -z : z;
            				
	            			BlockPos blockPos1 = new BlockPos(d5 + randomX, d6 + randomY, d7 + randomZ);
	            			BlockPos blockPos2 = new BlockPos(d5 + randomX, d6 + randomY + 1, d7 + randomZ);
	            			BlockPos blockPos3 = new BlockPos(d5 + randomX, d6 + randomY + 2, d7 + randomZ);
	            			IBlockState blockState1 = destinationWorld.getBlockState(blockPos1);
	            			IBlockState blockState2 = destinationWorld.getBlockState(blockPos2);
	            			IBlockState blockState3 = destinationWorld.getBlockState(blockPos3);
	            			
	            			if(
            					blockState1.getMaterial().blocksMovement() &&
            					!blockState2.getMaterial().blocksMovement() &&
            					!blockState3.getMaterial().blocksMovement() &&
            					!blockState2.getMaterial().isLiquid() &&
            					!blockState3.getMaterial().isLiquid() &&
            					blockState2.getBlock() != Blocks.FIRE &&
            					blockState3.getBlock() != Blocks.FIRE &&
            					blockState2.getBlock() != Blocks.PORTAL &&
            					blockState3.getBlock() != Blocks.PORTAL
        					)
	            			{
	                			entityIn.setLocationAndAngles(blockPos2.getX() + 0.5, blockPos2.getY(), blockPos2.getZ() + 0.5, entityIn.rotationYaw, entityIn.rotationPitch);
		            			return true;
		            		}
	            		}
            		}
            	}   
            	// Could not find a suitable spawn location, have to spawn player inside portal so destroy portal so that the player doesn't get teleported back and forth 
            	destinationWorld.notifyNeighborsOfStateChange(new BlockPos(d5, d6 + 1, d7), destinationWorld.getBlockState(new BlockPos(d5, d6 + 1, d7)).getBlock());
            	entityIn.setLocationAndAngles(d5, d6 + 1, d7, entityIn.rotationYaw, entityIn.rotationPitch);            	
            }

            return true;
        } else {
            return false;
        }
    }
    
    public static void placeInPortal(ForgeMaterialData portalMaterial, WorldServer destinationWorld, Entity entityIn, float rotationYaw, Teleporter _this)
    {
        if (destinationWorld.provider.getDimensionType().getId() != 1) // If not End
        {
            if (!placeInExistingPortal(destinationWorld, entityIn, rotationYaw, _this))
            {
            	makePortal(portalMaterial, destinationWorld, entityIn, _this);
            	placeInExistingPortal(destinationWorld, entityIn, rotationYaw, _this);
            }
        } else {
            int i = MathHelper.floor_double(entityIn.posX);
            int j = MathHelper.floor_double(entityIn.posY) - 1;
            int k = MathHelper.floor_double(entityIn.posZ);

            for (int j1 = -2; j1 <= 2; ++j1)
            {
                for (int k1 = -2; k1 <= 2; ++k1)
                {
                    for (int l1 = -1; l1 < 3; ++l1)
                    {
                        int i2 = i + k1 * 1 + j1 * 0;
                        int j2 = j + l1;
                        int k2 = k + k1 * 0 - j1 * 1;
                        boolean flag = l1 < 0;	                        	                        
                        
                        destinationWorld.setBlockState(new BlockPos(i2, j2, k2), flag ? portalMaterial.internalBlock() : Blocks.AIR.getDefaultState());
                    }
                }
            }

            entityIn.setLocationAndAngles((double)i, (double)j + 1, (double)k, entityIn.rotationYaw, 0.0F);
            entityIn.motionX = 0.0D;
            entityIn.motionY = 0.0D;
            entityIn.motionZ = 0.0D;
        }
    }
    
    public static boolean makePortal(ForgeMaterialData portalMaterial, WorldServer destinationWorld, Entity entityIn, Teleporter _this)
    {
        double d0 = -1.0D;
        int j = MathHelper.floor_double(entityIn.posX);
        int k = MathHelper.floor_double(entityIn.posY);
        int l = MathHelper.floor_double(entityIn.posZ);
        int i1 = j;
        int j1 = k;
        int k1 = l;
        int l1 = 0;
        int i2 = new Random().nextInt(4);
        BlockPos.MutableBlockPos blockpos$mutableblockpos = new BlockPos.MutableBlockPos();

        for (int j2 = j - 16; j2 <= j + 16; ++j2)
        {
            double d1 = (double)j2 + 0.5D - entityIn.posX;

            for (int l2 = l - 16; l2 <= l + 16; ++l2)
            {
                double d2 = (double)l2 + 0.5D - entityIn.posZ;
                label146:

                for (int j3 = destinationWorld.getActualHeight() - 1; j3 >= 0; --j3)
                {
                    if (destinationWorld.isAirBlock(blockpos$mutableblockpos.setPos(j2, j3, l2)))
                    {
                        while (j3 > 0 && destinationWorld.isAirBlock(blockpos$mutableblockpos.setPos(j2, j3 - 1, l2)))
                        {
                            --j3;
                        }

                        for (int k3 = i2; k3 < i2 + 4; ++k3)
                        {
                            int l3 = k3 % 2;
                            int i4 = 1 - l3;

                            if (k3 % 4 >= 2)
                            {
                                l3 = -l3;
                                i4 = -i4;
                            }

                            for (int j4 = 0; j4 < 3; ++j4)
                            {
                                for (int k4 = 0; k4 < 4; ++k4)
                                {
                                    for (int l4 = -1; l4 < 4; ++l4)
                                    {
                                        int i5 = j2 + (k4 - 1) * l3 + j4 * i4;
                                        int j5 = j3 + l4;
                                        int k5 = l2 + (k4 - 1) * i4 - j4 * l3;
                                        blockpos$mutableblockpos.setPos(i5, j5, k5);

                                        if (l4 < 0 && !destinationWorld.getBlockState(blockpos$mutableblockpos).getMaterial().isSolid() || l4 >= 0 && !destinationWorld.isAirBlock(blockpos$mutableblockpos))
                                        {
                                            continue label146;
                                        }
                                    }
                                }
                            }

                            double d5 = (double)j3 + 0.5D - entityIn.posY;
                            double d7 = d1 * d1 + d5 * d5 + d2 * d2;

                            if (d0 < 0.0D || d7 < d0)
                            {
                                d0 = d7;
                                i1 = j2;
                                j1 = j3;
                                k1 = l2;
                                l1 = k3 % 4;
                            }
                        }
                    }
                }
            }
        }

        if (d0 < 0.0D)
        {
            for (int l5 = j - 16; l5 <= j + 16; ++l5)
            {
                double d3 = (double)l5 + 0.5D - entityIn.posX;

                for (int j6 = l - 16; j6 <= l + 16; ++j6)
                {
                    double d4 = (double)j6 + 0.5D - entityIn.posZ;
                    label567:

                    for (int i7 = destinationWorld.getActualHeight() - 1; i7 >= 0; --i7)
                    {
                        if (destinationWorld.isAirBlock(blockpos$mutableblockpos.setPos(l5, i7, j6)))
                        {
                            while (i7 > 0 && destinationWorld.isAirBlock(blockpos$mutableblockpos.setPos(l5, i7 - 1, j6)))
                            {
                                --i7;
                            }

                            for (int k7 = i2; k7 < i2 + 2; ++k7)
                            {
                                int j8 = k7 % 2;
                                int j9 = 1 - j8;

                                for (int j10 = 0; j10 < 4; ++j10)
                                {
                                    for (int j11 = -1; j11 < 4; ++j11)
                                    {
                                        int j12 = l5 + (j10 - 1) * j8;
                                        int i13 = i7 + j11;
                                        int j13 = j6 + (j10 - 1) * j9;
                                        blockpos$mutableblockpos.setPos(j12, i13, j13);

                                        if (j11 < 0 && !destinationWorld.getBlockState(blockpos$mutableblockpos).getMaterial().isSolid() || j11 >= 0 && !destinationWorld.isAirBlock(blockpos$mutableblockpos))
                                        {
                                            continue label567;
                                        }
                                    }
                                }

                                double d6 = (double)i7 + 0.5D - entityIn.posY;
                                double d8 = d3 * d3 + d6 * d6 + d4 * d4;

                                if (d0 < 0.0D || d8 < d0)
                                {
                                    d0 = d8;
                                    i1 = l5;
                                    j1 = i7;
                                    k1 = j6;
                                    l1 = k7 % 2;
                                }
                            }
                        }
                    }
                }
            }
        }

        int i6 = i1;
        int k2 = j1;
        int k6 = k1;
        int l6 = l1 % 2;
        int i3 = 1 - l6;

        if (l1 % 4 >= 2)
        {
            l6 = -l6;
            i3 = -i3;
        }

        if (d0 < 0.0D)
        {
            j1 = MathHelper.clamp_int(j1, 70, destinationWorld.getActualHeight() - 10);
            k2 = j1;

            for (int j7 = -1; j7 <= 1; ++j7)
            {
                for (int l7 = 1; l7 < 3; ++l7)
                {
                    for (int k8 = -1; k8 < 3; ++k8)
                    {
                        int k9 = i6 + (l7 - 1) * l6 + j7 * i3;
                        int k10 = k2 + k8;
                        int k11 = k6 + (l7 - 1) * i3 - j7 * l6;
                        boolean flag = k8 < 0;
                        destinationWorld.setBlockState(new BlockPos(k9, k10, k11), flag ? portalMaterial.internalBlock() : Blocks.AIR.getDefaultState());
                    }
                }
            }
        }

        IBlockState iblockstate = Blocks.PORTAL.getDefaultState().withProperty(BlockPortal.AXIS, l6 == 0 ? EnumFacing.Axis.Z : EnumFacing.Axis.X);

        for (int i8 = 0; i8 < 4; ++i8)
        {
            for (int l8 = 0; l8 < 4; ++l8)
            {
                for (int l9 = -1; l9 < 4; ++l9)
                {
                    int l10 = i6 + (l8 - 1) * l6;
                    int l11 = k2 + l9;
                    int k12 = k6 + (l8 - 1) * i3;
                    boolean flag1 = l8 == 0 || l8 == 3 || l9 == -1 || l9 == 3;
                    destinationWorld.setBlockState(new BlockPos(l10, l11, k12), flag1 ? portalMaterial.internalBlock() : iblockstate, 2);
                }
            }
        }

        return true;
    }
}
