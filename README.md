# 🚗 UberCraft - Cryptocurrency-Powered Transportation System

===============================================================================
| Minecraft Version | Java Version | Spigot API | License    | Version       |
|-------------------|--------------|------------|------------|---------------|
| 1.20+             | 17+          | 1.20+      | MIT        | 1.0.0         |
===============================================================================

## 📋 TABLE OF CONTENTS
===============================================================================
1. ABOUT THE PROJECT
2. FEATURES
3. HOW IT WORKS
4. COMMANDS
5. PERMISSIONS
6. PAYMENT SYSTEM
7. INSTALLATION
8. CONFIGURATION
9. WARP SYSTEM
10. API INTEGRATION
11. DEPENDENCIES
12. USAGE EXAMPLES
13. SCREENSHOTS
14. FAQ
15. CHANGELOG
16. LICENSE
17. SUPPORT
===============================================================================


## 🎯 ABOUT THE PROJECT
===============================================================================
UberCraft is an innovative plugin that brings the Uber experience to Minecraft!
Players can request rides to specific coordinates or warps, paying with 
cryptocurrency through the **CoinCard** system. "Ubers" (staff or players with 
permission) can accept these rides and receive real-time payments.

### 🎮 Realistic Experience
- 🚕 **Request a ride** just like real Uber
- 🧭 **Smart compass** that guides the driver
- 💰 **Automatic payments** with cryptocurrency
- ⚖️ **Fair cancellation** and refund system
- 🌍 **Multi-world** - works in all dimensions

===============================================================================


## ✨ FEATURES
===============================================================================

### 🚀 For Players
| Feature                    | Description                                      |
|----------------------------|--------------------------------------------------|
| 📍 **Ride to coordinates** | `/uber x y z [world]` - Request Uber anywhere    |
| 🗺️ **Ride to warps**       | `/uber warp` - Choose a warp from the menu       |
| ❌ **Cancellation**         | `/uber cancel` - Cancel your current ride        |
| 💵 **Fair payment**         | Pay only for what you use                         |

### 👨‍✈️ For Ubers (Drivers)
| Feature                    | Description                                      |
|----------------------------|--------------------------------------------------|
| 📋 **Request menu**         | `/ubergui` - View all pending rides              |
| 🧭 **Smart compass**        | Points to passenger first, then to destination   |
| 💰 **Earn money**           | Receive 90% of the ride value                    |
| 📊 **Stage system**         | Pickup → Boarding → Destination                   |

### ⚙️ For Administrators
| Feature                    | Description                                      |
|----------------------------|--------------------------------------------------|
| 🏗️ **Manage warps**         | `/uber admin set <name>` - Create warps with icons|
| ❌ **Remove warps**         | `/uber admin unset <name>` - Remove existing warps|
| ⚡ **100% asynchronous**    | Zero server lag                                  |
| 🔧 **Fully configurable**   | All messages and values in config.yml            |

===============================================================================


## 🎮 HOW IT WORKS
===============================================================================

### 📊 Ride Flow

┌─────────────────┐
│ PLAYER │
│ /uber warp │
└────────┬────────┘
↓
┌─────────────────┐
│ PAYS 100% │
│ (to server) │
└────────┬────────┘
↓
┌─────────────────┐
│ UBER ACCEPTS │
│ /ubergui │
└────────┬────────┘
↓
┌─────────────────┐ ┌─────────────────┐
│ STAGE 1 │────▶│ Arrived at │
│ Compass points │ │ PASSENGER │
│ to PICKUP │ │ (distance <5) │
└────────┬────────┘ └────────┬────────┘
│ │
└───────────────────────┘
↓
┌─────────────────┐
│ STAGE 2 │
│ "BOARDING" │
│ Message to │
│ passenger │
└────────┬────────┘
↓
┌─────────────────┐ ┌─────────────────┐
│ STAGE 3 │────▶│ Arrived at │
│ Compass points │ │ DESTINATION │
│ to DESTINATION │ │ (distance <5) │
└────────┬────────┘ └────────┬────────┘
│ │
└───────────────────────┘
↓
┌─────────────────┐
│ RIDE │
│ COMPLETE! │
│ Uber gets 90% │
│ Server 10% │
└─────────────────┘


===============================================================================


## 📝 COMMANDS
===============================================================================

### 👤 Player Commands
| Command                      | Description                         | Permission     |
|------------------------------|-------------------------------------|----------------|
| `/uber x y z [world]`        | Request Uber to coordinates         | `uber.player`  |
| `/uber warp`                  | Open warp selection menu            | `uber.player`  |
| `/uber warp <name>`           | Request Uber to specific warp       | `uber.player`  |
| `/uber cancel`                | Cancel current ride                 | `uber.player`  |

### 👨‍✈️ Uber Commands
| Command                      | Description                         | Permission     |
|------------------------------|-------------------------------------|----------------|
| `/ubergui`                    | Open pending requests menu          | `uber.uber`    |

### ⚙️ Admin Commands
| Command                      | Description                         | Permission     |
|------------------------------|-------------------------------------|----------------|
| `/uber admin set <name>`      | Create warp with item in hand       | `uber.admin`   |
| `/uber admin unset <name>`    | Remove existing warp                | `uber.admin`   |

===============================================================================


## 🔐 PERMISSIONS
===============================================================================

```
uber.player:
  description: Basic permission for players to request Ubers
  default: true

uber.uber:
  description: Permission to be an Uber and accept rides
  default: op

uber.admin:
  description: Permission to manage warps
  default: op
  children:
    uber.uber: true

```

===============================================================================

💰 PAYMENT SYSTEM
===============================================================================

📊 Distribution Table
Situation	Player	Uber	Server
Normal ride	Pays 100%	Gets 90%	Keeps 10%
Player cancels	Gets 40%	Gets 50%	Keeps 10%
Uber cancels	Gets 90%	Gets 0%	Keeps 10%
Uber leaves server	Gets 100%	Gets 0%	Keeps 0%
Expired (1 min)	Gets 90%	-	Keeps 10%


🔄 Payment Flow

1. RIDE START
   Player → Pays 100% → Server
   
2. RIDE COMPLETE (success)
   Server → Pays 90% → Uber
   Server → Keeps 10% (fee)

3. PLAYER CANCELLATION
   Server → Pays 50% → Uber
   Server → Pays 40% → Player
   Server → Keeps 10%

4. UBER CANCELLATION
   Server → Pays 90% → Player
   Server → Keeps 10%
   Uber → Gets 0% (penalty)

5. UBER LEFT SERVER
   Server → Pays 100% → Player

💳 CoinCard Integration
Uses CoinCard card system for transactions

Payment queue with 1-second delay between transactions

Truncated to 8 decimal places for precision

Fully asynchronous, zero lag

===============================================================================

📥 INSTALLATION
===============================================================================

Prerequisites
✅ Spigot/Paper server 1.20+

✅ Java 17 or higher

✅ CoinCard plugin installed

✅ (Optional) Vault for integrations

Thanks for downloading!!!

:D
