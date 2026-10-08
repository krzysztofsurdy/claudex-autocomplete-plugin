<?php

declare(strict_types=1);

namespace App\Repository;

use App\Entity\LearningTeamParticipant;

class LearningTeamParticipantRepository
{
    public function find(int $id): ?LearningTeamParticipant
    {
        return null;
    }

    /**
     * @return list<LearningTeamParticipant>
     */
    public function findByLearningTeamId(int $learningTeamId): array
    {
        return [];
    }
}
