package com.gym.app

enum class Muscle(val label: String) {
    Chest("Chest"),
    Back("Back"),
    Shoulders("Shoulders"),
    Biceps("Biceps"),
    Triceps("Triceps"),
    Legs("Legs"),
    Glutes("Glutes"),
    Core("Core"),
    FullBody("Full body & cardio"),
    Other("Other"),
}

enum class Equipment(val label: String) {
    Barbell("Barbell"),
    Dumbbell("Dumbbell"),
    Kettlebell("Kettlebell"),
    Machine("Machine"),
    Cable("Cable"),
    Bodyweight("Bodyweight"),
    Other("Other"),
}

data class CatalogExercise(
    val name: String,
    val muscle: Muscle,
    val equipment: Equipment,
    /** Folder id in free-exercise-db (public domain), or null if there's no picture. */
    val imageId: String? = null,
    val custom: Boolean = false,
    /** For your own exercises: the CustomExercise id (its photo is stored under this id). */
    val customId: String? = null,
) {
    /** Start and end position photos. */
    private val photoId: String? get() = imageId?.takeIf { it !in ExerciseInstructions.noPhotos }
    val imageUrls: List<String> get() = photoId?.let { id -> listOf(imageUrl(id, 0), imageUrl(id, 1)) }.orEmpty()
    val thumbnailUrl: String? get() = photoId?.let { imageUrl(it, 0) }
    val instructions: List<String> get() = imageId?.let { ExerciseInstructions.byId[it] }.orEmpty()
}

// Photos and instructions: github.com/yuhonas/free-exercise-db (The Unlicense), served by jsDelivr
// and pinned to a commit so they can't change under us.
private const val IMAGE_BASE =
    "https://cdn.jsdelivr.net/gh/yuhonas/free-exercise-db@f00c92c7dcf1216a928a52c3706c7ce8e2f71ed5/exercises"

private fun imageUrl(id: String, index: Int) = "$IMAGE_BASE/$id/$index.jpg"

/** Built-in exercises. Ones you add yourselves are stored in Firestore as [CustomExercise]. */
object Catalog {
    private class Group(val muscle: Muscle, val list: MutableList<CatalogExercise>) {
        fun ex(name: String, equipment: Equipment, imageId: String? = null) {
            list += CatalogExercise(name, muscle, equipment, imageId)
        }
    }

    private fun MutableList<CatalogExercise>.group(muscle: Muscle, block: Group.() -> Unit) =
        Group(muscle, this).block()

    val exercises: List<CatalogExercise> = buildList {
        group(Muscle.Chest) {
            ex("Bench press", Equipment.Barbell, "Barbell_Bench_Press_-_Medium_Grip")
            ex("Incline bench press", Equipment.Barbell, "Barbell_Incline_Bench_Press_-_Medium_Grip")
            ex("Decline bench press", Equipment.Barbell, "Decline_Barbell_Bench_Press")
            ex("Dumbbell bench press", Equipment.Dumbbell, "Dumbbell_Bench_Press")
            ex("Incline dumbbell press", Equipment.Dumbbell, "Incline_Dumbbell_Press")
            ex("Dumbbell fly", Equipment.Dumbbell, "Dumbbell_Flyes")
            ex("Kettlebell floor press", Equipment.Kettlebell, "One-Arm_Kettlebell_Floor_Press")
            ex("Cable crossover", Equipment.Cable, "Cable_Crossover")
            ex("Chest press machine", Equipment.Machine, "Machine_Bench_Press")
            ex("Pec deck", Equipment.Machine, "Butterfly")
            ex("Push-up", Equipment.Bodyweight, "Pushups")
            ex("Dips", Equipment.Bodyweight, "Dips_-_Chest_Version")
        }
        group(Muscle.Back) {
            ex("Deadlift", Equipment.Barbell, "Barbell_Deadlift")
            ex("Barbell row", Equipment.Barbell, "Bent_Over_Barbell_Row")
            ex("Pendlay row", Equipment.Barbell)
            ex("T-bar row", Equipment.Barbell, "T-Bar_Row_with_Handle")
            ex("Rack pull", Equipment.Barbell, "Rack_Pulls")
            ex("Single-arm dumbbell row", Equipment.Dumbbell, "One-Arm_Dumbbell_Row")
            ex("Kettlebell row", Equipment.Kettlebell, "One-Arm_Kettlebell_Row")
            ex("Renegade row", Equipment.Kettlebell, "Alternating_Renegade_Row")
            ex("Pull-up", Equipment.Bodyweight, "Pullups")
            ex("Chin-up", Equipment.Bodyweight, "Chin-Up")
            ex("Assisted pull-up", Equipment.Machine, "Band_Assisted_Pull-Up")
            ex("Lat pulldown", Equipment.Cable, "Wide-Grip_Lat_Pulldown")
            ex("Seated cable row", Equipment.Cable, "Seated_Cable_Rows")
            ex("Straight-arm pulldown", Equipment.Cable, "Straight-Arm_Pulldown")
            ex("Face pull", Equipment.Cable, "Face_Pull")
            ex("Back extension", Equipment.Bodyweight, "Hyperextensions_Back_Extensions")
        }
        group(Muscle.Shoulders) {
            ex("Overhead press", Equipment.Barbell, "Standing_Military_Press")
            ex("Seated dumbbell press", Equipment.Dumbbell, "Seated_Dumbbell_Press")
            ex("Arnold press", Equipment.Dumbbell, "Arnold_Dumbbell_Press")
            ex("Lateral raise", Equipment.Dumbbell, "Side_Lateral_Raise")
            ex("Front raise", Equipment.Dumbbell, "Front_Dumbbell_Raise")
            ex("Rear delt fly", Equipment.Dumbbell, "Seated_Bent-Over_Rear_Delt_Raise")
            ex("Shrug", Equipment.Dumbbell, "Dumbbell_Shrug")
            ex("Upright row", Equipment.Barbell, "Upright_Barbell_Row")
            ex("Kettlebell press", Equipment.Kettlebell, "Alternating_Kettlebell_Press")
            ex("Kettlebell push press", Equipment.Kettlebell, "One-Arm_Kettlebell_Push_Press")
            ex("Kettlebell Arnold press", Equipment.Kettlebell, "Kettlebell_Arnold_Press")
            ex("Kettlebell halo", Equipment.Kettlebell, "Kettlebell_Halo")
            ex("Cable lateral raise", Equipment.Cable, "Cable_Seated_Lateral_Raise")
            ex("Shoulder press machine", Equipment.Machine, "Machine_Shoulder_Military_Press")
            ex("Reverse pec deck", Equipment.Machine, "Reverse_Machine_Flyes")
        }
        group(Muscle.Biceps) {
            ex("Barbell curl", Equipment.Barbell, "Barbell_Curl")
            ex("EZ-bar preacher curl", Equipment.Barbell, "Preacher_Curl")
            ex("Dumbbell curl", Equipment.Dumbbell, "Dumbbell_Bicep_Curl")
            ex("Hammer curl", Equipment.Dumbbell, "Hammer_Curls")
            ex("Incline dumbbell curl", Equipment.Dumbbell, "Incline_Dumbbell_Curl")
            ex("Concentration curl", Equipment.Dumbbell, "Concentration_Curls")
            ex("Cable curl", Equipment.Cable, "Standing_Biceps_Cable_Curl")
            ex("Preacher curl machine", Equipment.Machine, "Machine_Preacher_Curls")
        }
        group(Muscle.Triceps) {
            ex("Close-grip bench press", Equipment.Barbell, "Close-Grip_Barbell_Bench_Press")
            ex("Skull crusher", Equipment.Barbell, "EZ-Bar_Skullcrusher")
            ex("Triceps pushdown", Equipment.Cable, "Triceps_Pushdown")
            ex("Overhead cable extension", Equipment.Cable, "Cable_Rope_Overhead_Triceps_Extension")
            ex("Dumbbell overhead extension", Equipment.Dumbbell, "Standing_Dumbbell_Triceps_Extension")
            ex("Kettlebell overhead extension", Equipment.Kettlebell, "Kettlebell_Overhead_Triceps_Extension")
            ex("Triceps kickback", Equipment.Dumbbell, "Tricep_Dumbbell_Kickback")
            ex("Bench dip", Equipment.Bodyweight, "Bench_Dips")
        }
        group(Muscle.Legs) {
            ex("Back squat", Equipment.Barbell, "Barbell_Squat")
            ex("Front squat", Equipment.Barbell, "Front_Barbell_Squat")
            ex("Romanian deadlift", Equipment.Barbell, "Romanian_Deadlift")
            ex("Goblet squat", Equipment.Kettlebell, "Goblet_Squat")
            ex("Double kettlebell front squat", Equipment.Kettlebell, "Front_Squats_With_Two_Kettlebells")
            ex("Kettlebell single-leg deadlift", Equipment.Kettlebell, "Kettlebell_One-Legged_Deadlift")
            ex("Kettlebell pistol squat", Equipment.Kettlebell, "Kettlebell_Pistol_Squat")
            ex("Bulgarian split squat", Equipment.Dumbbell, "Split_Squat_with_Dumbbells")
            ex("Walking lunge", Equipment.Dumbbell, "Dumbbell_Lunges")
            ex("Step-up", Equipment.Dumbbell, "Dumbbell_Step_Ups")
            ex("Leg press", Equipment.Machine, "Leg_Press")
            ex("Hack squat", Equipment.Machine, "Hack_Squat")
            ex("Leg extension", Equipment.Machine, "Leg_Extensions")
            ex("Lying leg curl", Equipment.Machine, "Lying_Leg_Curls")
            ex("Seated leg curl", Equipment.Machine, "Seated_Leg_Curl")
            ex("Standing calf raise", Equipment.Machine, "Standing_Calf_Raises")
            ex("Seated calf raise", Equipment.Machine, "Seated_Calf_Raise")
        }
        group(Muscle.Glutes) {
            ex("Hip thrust", Equipment.Barbell, "Barbell_Hip_Thrust")
            ex("Sumo deadlift", Equipment.Barbell, "Sumo_Deadlift")
            ex("Good morning", Equipment.Barbell, "Good_Morning")
            ex("Kettlebell swing", Equipment.Kettlebell, "One-Arm_Kettlebell_Swings")
            ex("Glute bridge", Equipment.Bodyweight, "Butt_Lift_Bridge")
            ex("Cable kickback", Equipment.Cable, "One-Legged_Cable_Kickback")
            ex("Hip abduction machine", Equipment.Machine, "Thigh_Abductor")
            ex("Hip adduction machine", Equipment.Machine, "Thigh_Adductor")
        }
        group(Muscle.Core) {
            ex("Plank", Equipment.Bodyweight, "Plank")
            ex("Side plank", Equipment.Bodyweight, "Side_Bridge")
            ex("Crunch", Equipment.Bodyweight, "Crunches")
            ex("Decline sit-up", Equipment.Bodyweight)
            ex("Hanging leg raise", Equipment.Bodyweight, "Hanging_Leg_Raise")
            ex("Dead bug", Equipment.Bodyweight, "Dead_Bug")
            ex("Kettlebell windmill", Equipment.Kettlebell, "Kettlebell_Windmill")
            ex("Kettlebell figure 8", Equipment.Kettlebell, "Kettlebell_Figure_8")
            ex("Ab wheel rollout", Equipment.Other, "Ab_Roller")
            ex("Russian twist", Equipment.Other, "Russian_Twist")
            ex("Cable crunch", Equipment.Cable, "Cable_Crunch")
            ex("Pallof press", Equipment.Cable, "Pallof_Press")
        }
        group(Muscle.FullBody) {
            ex("Clean and press", Equipment.Barbell, "Clean_and_Press")
            ex("Kettlebell clean", Equipment.Kettlebell, "One-Arm_Kettlebell_Clean")
            ex("Kettlebell clean and jerk", Equipment.Kettlebell, "One-Arm_Kettlebell_Clean_and_Jerk")
            ex("Kettlebell snatch", Equipment.Kettlebell, "One-Arm_Kettlebell_Snatch")
            ex("Kettlebell thruster", Equipment.Kettlebell, "Kettlebell_Thruster")
            ex("Kettlebell sumo high pull", Equipment.Kettlebell, "Kettlebell_Sumo_High_Pull")
            ex("Turkish get-up", Equipment.Kettlebell, "Kettlebell_Turkish_Get-Up_Squat_style")
            ex("Farmer's carry", Equipment.Dumbbell, "Farmers_Walk")
            ex("Burpee", Equipment.Bodyweight)
            ex("Box jump", Equipment.Bodyweight, "Front_Box_Jump")
            ex("Battle ropes", Equipment.Other, "Battling_Ropes")
            ex("Rowing machine", Equipment.Machine, "Rowing_Stationary")
            ex("Treadmill", Equipment.Machine, "Running_Treadmill")
            ex("Exercise bike", Equipment.Machine, "Bicycling_Stationary")
            ex("Elliptical", Equipment.Machine, "Elliptical_Trainer")
            ex("Stair climber", Equipment.Machine, "Stairmaster")
        }
    }

    private val byKey: Map<String, CatalogExercise> = exercises.associateBy { exerciseKey(it.name) }

    /** Built-in entry for an exercise name (used to show pictures on workout/plan cards). */
    fun find(name: String): CatalogExercise? = byKey[exerciseKey(name)]
}

fun CustomExercise.toCatalog() = CatalogExercise(
    name = name,
    muscle = Muscle.entries.firstOrNull { it.name == muscle } ?: Muscle.Other,
    equipment = Equipment.entries.firstOrNull { it.name == equipment } ?: Equipment.Other,
    custom = true,
    customId = id,
)
