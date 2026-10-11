import { mkdirSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';

const OUTPUT = join(import.meta.dirname, 'fixtures', 'core_math_fixtures.json');

function int32(value) {
    return value | 0;
}

function iterateSeed(input) {
    let seed = int32(input);
    if (seed === 0) seed = -1;
    seed = int32(seed ^ int32(seed << 13));
    seed = int32(seed ^ (seed >> 17));
    seed = int32(seed ^ int32(seed << 5));
    return seed;
}

function contribution(values) {
    let total = 0;
    for (let index = 0; index < values.length; index++) {
        total = int32(total + Math.imul(int32(values[index]), index + 1));
    }
    return total;
}

function checksum(turnNumber, objects) {
    let total = iterateSeed(turnNumber);
    for (const object of [...objects].sort((a, b) => a.objectId - b.objectId)) {
        total = int32(total + contribution(object.values));
    }
    return total;
}

const seedInputs = [0, 1, 2, 5, 127, 32767, 2147483647, -1, -2, -2147483648];
const contributionCases = [
    {
        name: 'machine-basic',
        objectType: 'machine',
        values: [9600, 12800, 3]
    },
    {
        name: 'avatar-basic',
        objectType: 'avatar',
        values: [3200, 6400, 2, 4, 5, 0, 0, 0, 1, 2, 9600, 12800, 6, 42, 1, 7]
    },
    {
        name: 'snowball-basic',
        objectType: 'snowball',
        values: [6400, 3200, 4000, 90, 0, 13, 7, 6]
    },
    {
        name: 'signed-overflow',
        objectType: 'diagnostic',
        values: [2147483647, -2147483648, 1073741824, -1073741824, 65536]
    }
].map(record => ({ ...record, contribution: contribution(record.values) }));

const mixedObjects = [
    { objectId: 10, objectType: 'machine', values: contributionCases[0].values },
    { objectId: 7, objectType: 'avatar', values: contributionCases[1].values },
    { objectId: 2, objectType: 'snowball', values: contributionCases[2].values }
];

const fixture = {
    schemaVersion: 1,
    source: {
        cast: 'hh_gamesys.cct',
        classes: ['CIterateSeed', 'Gamesystem Gameobject Class', 'Gamesystem Component Class'],
        rule: 'start-of-turn seed plus insertion-ordered integer sync values, objects by numeric ID'
    },
    arithmetic: 'signed-32-bit-wrap; arithmetic-right-shift',
    seedVectors: seedInputs.map(input => ({ input, output: iterateSeed(input) })),
    contributionVectors: contributionCases,
    checksumVectors: [
        { name: 'empty-turn-zero', turnNumber: 0, objects: [], checksum: checksum(0, []) },
        { name: 'empty-turn-five', turnNumber: 5, objects: [], checksum: checksum(5, []) },
        {
            name: 'mixed-object-order',
            turnNumber: 5,
            objects: mixedObjects,
            sortedObjectIds: mixedObjects.map(value => value.objectId).sort((a, b) => a - b),
            checksum: checksum(5, mixedObjects)
        },
        {
            name: 'mixed-object-reversed-input',
            turnNumber: 5,
            objects: [...mixedObjects].reverse(),
            sortedObjectIds: mixedObjects.map(value => value.objectId).sort((a, b) => a - b),
            checksum: checksum(5, [...mixedObjects].reverse())
        }
    ]
};

if (fixture.checksumVectors[2].checksum !== fixture.checksumVectors[3].checksum) {
    throw new Error('Object-ID sorting is not deterministic');
}

mkdirSync(join(import.meta.dirname, 'fixtures'), { recursive: true });
writeFileSync(OUTPUT, JSON.stringify(fixture, null, 2) + '\n');
console.log(OUTPUT);

