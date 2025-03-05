import dev.zwazel.GameWorld;
import dev.zwazel.PropertyHandler;
import dev.zwazel.bot.BotInterface;
import dev.zwazel.internal.PublicGameWorld;
import dev.zwazel.internal.connection.client.ConnectedClientConfig;
import dev.zwazel.internal.game.lobby.TeamConfig;
import dev.zwazel.internal.game.state.ClientState;
import dev.zwazel.internal.game.tank.Tank;
import dev.zwazel.internal.game.tank.TankConfig;
import dev.zwazel.internal.game.tank.implemented.LightTank;
import dev.zwazel.internal.message.MessageContainer;
import dev.zwazel.internal.message.MessageData;
import dev.zwazel.internal.message.data.GameConfig;
import dev.zwazel.internal.message.data.SimpleTextMessage;
import dev.zwazel.internal.message.data.tank.GotHit;
import dev.zwazel.internal.message.data.tank.Hit;

import java.util.List;
import java.util.Optional;

import static dev.zwazel.internal.message.MessageTarget.Type.CLIENT;

public class MyBot implements BotInterface {
    private final PropertyHandler propertyHandler = PropertyHandler.getInstance();
    private final float minAttackDistance;
    private final float maxAttackDistance;

    private List<ConnectedClientConfig> teamMembers;
    private List<ConnectedClientConfig> enemyTeamMembers;

    public MyBot() {
        this.minAttackDistance = Float.parseFloat(propertyHandler.getProperty("bot.attack.minDistance"));
        this.maxAttackDistance = Float.parseFloat(propertyHandler.getProperty("bot.attack.maxDistance"));
    }

    public void start() {
        // GameWorld.startGame(this, LightTank.class); // This starts the game with a LightTank, and immediately starts the game when connected
        GameWorld.connectToServer(this, LightTank.class); // This connects to the server with a LightTank, but does not immediately start the game
    }

    @Override
    public void setup(PublicGameWorld world) {
        GameConfig config = world.getGameConfig();

        TeamConfig myTeamConfig = config.getMyTeamConfig();
        TeamConfig enemyTeamConfig = config.teamConfigs().values().stream()
                .filter(teamConfig -> !teamConfig.teamName().equals(myTeamConfig.teamName()))
                .findFirst()
                .orElseThrow();

        // Get all team members, excluding myself
        teamMembers = config.getTeamMembers(myTeamConfig.teamName(), config.clientId());
        // Get all enemy team members
        enemyTeamMembers = config.getTeamMembers(enemyTeamConfig.teamName());
    }

    @Override
    public void processTick(PublicGameWorld world) {
        ClientState myClientState = world.getMyState();

        if (myClientState.state() == ClientState.PlayerState.DEAD) {
            System.out.println("I'm dead!");
            return;
        }

        LightTank tank = (LightTank) world.getTank();
        // HeavyTank tank = (HeavyTank) world.getTank();
        // SelfPropelledArtillery tank = (SelfPropelledArtillery) world.getTank();
        TankConfig myTankConfig = tank.getConfig(world);
        GameConfig config = world.getGameConfig();

        // Get the closest enemy tank
        Optional<ClientState> closestEnemy = enemyTeamMembers.stream()
                .map(connectedClientConfig -> world.getClientState(connectedClientConfig.clientId()))
                // Filter out null states, states without a position and dead states
                .filter(clientState -> clientState != null && clientState.transformBody().getTranslation() != null &&
                        clientState.state() != ClientState.PlayerState.DEAD)
                .min((o1, o2) -> {
                    double distance1 = myClientState.transformBody().getTranslation().distance(o1.transformBody().getTranslation());
                    double distance2 = myClientState.transformBody().getTranslation().distance(o2.transformBody().getTranslation());
                    return Double.compare(distance1, distance2);
                });

        // Move towards the closest enemy and shoot when close enough, or move in a circle if no enemies are found
        closestEnemy.ifPresentOrElse(
                enemy -> {
                    // If enemy is within attack range, shoot; otherwise, move accordingly
                    double distanceToEnemy = myClientState.transformBody().getTranslation().distance(enemy.transformBody().getTranslation());

                    if (distanceToEnemy < this.minAttackDistance) {
                        // Move away from enemy if too close
                        tank.moveTowards(world, Tank.MoveDirection.BACKWARD, enemy.transformBody().getTranslation(), true);
                    } else if (distanceToEnemy > this.maxAttackDistance) {
                        // Move towards enemy if too far
                        tank.moveTowards(world, Tank.MoveDirection.FORWARD, enemy.transformBody().getTranslation(), true);
                    }
                    tank.rotateTurretTowards(world, enemy.transformBody().getTranslation());

                    if (distanceToEnemy <= this.maxAttackDistance) {
                        // You can check if you can shoot before shooting
                        if (tank.canShoot(world)) {
                            // Or also just shoot, it will return false if you can't shoot.
                            // And by checking the world, if debug is enabled, you can print out a message.
                            if (tank.shoot(world) && world.isDebug()) {
                                System.out.println("Shot at enemy!");
                            }
                        }
                    }
                },
                () -> {
                    // No enemies found, move in a circle (negative is clockwise for yaw rotation)
                    tank.rotateBody(world, -myTankConfig.bodyRotationSpeed());
                    tank.move(world, Tank.MoveDirection.FORWARD);
                }
        );

        /*// Example of moving and rotating the tank
        tank.rotateBody(world, -myTankConfig.bodyRotationSpeed());
        tank.rotateTurretYaw(world, myTankConfig.turretYawRotationSpeed());
        // for pitch rotation, positive is down
        // tank.rotateTurretPitch(world, -myTankConfig.turretPitchRotationSpeed());
        tank.move(world, Tank.MoveDirection.FORWARD);*/

        // Get messages of a specific type only
        List<MessageContainer> hitMessages = world.getIncomingMessages(Hit.class);
        for (MessageContainer message : hitMessages) {
            Hit gotHitMessageData = (Hit) message.getMessage();
            handleHittingTank(world, gotHitMessageData);
        }

        // Get all messages
        List<MessageContainer> messages = world.getIncomingMessages();
        for (MessageContainer message : messages) {
            MessageData data = message.getMessage();

            switch (data) {
                case SimpleTextMessage textMessage ->
                        System.out.println("Received text message:\n\t" + textMessage.message());
                case GotHit gotHitMessageData -> handleGettingHit(world, gotHitMessageData);
                case Hit _ -> {
                    // We already handled this message type above
                }
                default -> System.err.println("Received unknown message type: " + data.getClass().getSimpleName());
            }
        }

        // Sending a nice message to all team members (individually, you could also send a single message to full team)
        teamMembers
                .forEach(target -> world.send(new MessageContainer(
                        CLIENT.get(target.clientId()),
                        new SimpleTextMessage(
                                "Hello " + target.clientName() + " from " + config.getMyConfig().clientName() + "!"
                        )
                )));

        // Sending a less nice message to all enemy team members
        enemyTeamMembers
                .forEach(target -> world.send(new MessageContainer(
                        CLIENT.get(target.clientId()),
                        new SimpleTextMessage(
                                "You're going down, " + target.clientName() + "!"
                        )
                )));
    }

    private void handleHittingTank(PublicGameWorld world, Hit hitMessageData) {
        ConnectedClientConfig targetConfig = world.getConnectedClientConfig(hitMessageData.hitEntity()).orElseThrow();
        TankConfig targetTankConfig = targetConfig.getTankConfig(world);
        TankConfig myTankConfig = world.getTank().getConfig(world);
        float armorOnHitSide = targetTankConfig.armor().get(hitMessageData.hitSide());
        float myExpectedDamage = myTankConfig.projectileDamage();
        float dealtDamage = hitMessageData.damageDealt();
        ClientState targetState = targetConfig.getClientState(world);
        System.out.println("Hit " + targetConfig.clientName() + " on " + hitMessageData.hitSide() + " side!");
        // print out how the damage was calculated
        System.out.println("Dealt damage: " + dealtDamage + " = " + myExpectedDamage + " * (1 - " + armorOnHitSide + ")");
        System.out.println(targetConfig.clientName() + " health: " + targetState.currentHealth());
    }

    private void handleGettingHit(PublicGameWorld world, GotHit gotHitMessageData) {
        ConnectedClientConfig shooterConfig = world.getConnectedClientConfig(gotHitMessageData.shooterEntity()).orElseThrow();
        System.out.println("Got hit by " + shooterConfig.clientName() + " on " + gotHitMessageData.hitSide());
        System.out.println("Received " + gotHitMessageData.damageReceived() + " damage!");
        System.out.println("Current health: " + world.getMyState().currentHealth());

        if (world.getMyState().state() == ClientState.PlayerState.DEAD) {
            System.out.println("I died! killed by " + shooterConfig.clientName());
        }
    }
}
