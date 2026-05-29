package com.overlay.models;

public class PlayerInfo {
    public int entityId;     
    public float x, y, z, screenX, screenY;  
    public int cd1, cd2, cd3, spell, campType, hp, hpMax;
    public String heroName, shortName;
    
    public PlayerInfo copy() {
        PlayerInfo p = new PlayerInfo();
        p.entityId = this.entityId; p.x = this.x; p.y = this.y; p.z = this.z;
        p.screenX = this.screenX; p.screenY = this.screenY; 
        p.cd1 = this.cd1; p.cd2 = this.cd2; p.cd3 = this.cd3; p.spell = this.spell;
        p.campType = this.campType; p.hp = this.hp; p.hpMax = this.hpMax; 
        p.heroName = this.heroName; p.shortName = this.shortName;
        return p;
    }
}
