<?php

declare(strict_types=1);

namespace App\Entity;

class LearningTeamParticipant
{
    public function __construct(private int $id, private int $studentId, private LearningTeam $learningTeam)
    {
    }

    public function getId(): int
    {
        return $this->id;
    }

    public function getStudentId(): int
    {
        return $this->studentId;
    }

    public function getLearningTeam(): LearningTeam
    {
        return $this->learningTeam;
    }
}
