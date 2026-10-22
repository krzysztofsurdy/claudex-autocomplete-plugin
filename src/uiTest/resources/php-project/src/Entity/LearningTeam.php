<?php

declare(strict_types=1);

namespace App\Entity;

class LearningTeam
{
    public function __construct(private int $id, private string $courseCode)
    {
    }

    public function getId(): int
    {
        return $this->id;
    }

    public function getCourseCode(): string
    {
        return $this->courseCode;
    }
}
